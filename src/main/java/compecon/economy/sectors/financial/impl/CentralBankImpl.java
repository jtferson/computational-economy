/*
Copyright (C) 2013 u.wol@wwu.de 
 
This file is part of ComputationalEconomy.

ComputationalEconomy is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

ComputationalEconomy is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with ComputationalEconomy. If not, see <http://www.gnu.org/licenses/>.
 */

package compecon.economy.sectors.financial.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.hibernate.annotations.Index;

import compecon.economy.bookkeeping.impl.BalanceSheetDTO;
import compecon.economy.materia.GoodType;
import compecon.economy.sectors.financial.BankAccount;
import compecon.economy.sectors.financial.BankAccount.MoneyType;
import compecon.economy.sectors.financial.BankAccount.TermType;
import compecon.economy.sectors.financial.BankAccountDelegate;
import compecon.economy.sectors.financial.BankCustomer;
import compecon.economy.sectors.financial.CentralBank;
import compecon.economy.sectors.financial.CreditBank;
import compecon.economy.sectors.financial.Currency;
import compecon.economy.sectors.state.State;
import compecon.economy.security.debt.FixedRateBond;
import compecon.engine.applicationcontext.ApplicationContext;
import compecon.engine.timesystem.TimeSystemEvent;
import compecon.engine.timesystem.impl.DayType;
import compecon.engine.timesystem.impl.HourType;
import compecon.engine.timesystem.impl.MonthType;
import compecon.math.util.MathUtil;

/**
 * Agent type central bank adjusts key interest rates based on price indices.
 */
@Entity
public class CentralBankImpl extends BankImpl implements CentralBank {

	@OneToOne(targetEntity = BankAccountImpl.class)
	@JoinColumn(name = "bankAccountCentralBankMoney_id")
	@Index(name = "IDX_A_BA_CENTRALBANKMONEY")
	// bank account for central bank money
	protected BankAccount bankAccountCentralBankMoney;

	@Column(name = "effectiveKeyInterestRate")
	protected double effectiveKeyInterestRate = 0.1;

	@Transient
	protected int NUMBER_OF_MARGINAL_PRICE_SNAPSHOTS_PER_DAY;

	@Transient
	protected StatisticalOffice statisticalOffice;

	@Override
	public void initialize() {
		super.initialize();

		// calculate interest
		final TimeSystemEvent interestCalculationEvent = new DailyInterestCalculationEvent();
		this.timeSystemEvents.add(interestCalculationEvent);
		ApplicationContext
				.getInstance()
				.getTimeSystem()
				.addEvent(interestCalculationEvent, -1, MonthType.EVERY,
						DayType.EVERY, HourType.HOUR_01);

		// take snapshots of marginal prices multiple times a day
		// -> market situation differs over the day !!!
		final TimeSystemEvent recalculateAveragePriceIndexEvent = new MarginalPriceSnapshotEvent();
		this.timeSystemEvents.add(recalculateAveragePriceIndexEvent);
		ApplicationContext
				.getInstance()
				.getTimeSystem()
				.addEvent(recalculateAveragePriceIndexEvent, -1,
						MonthType.EVERY, DayType.EVERY, HourType.HOUR_03);
		ApplicationContext
				.getInstance()
				.getTimeSystem()
				.addEvent(recalculateAveragePriceIndexEvent, -1,
						MonthType.EVERY, DayType.EVERY, HourType.HOUR_09);
		ApplicationContext
				.getInstance()
				.getTimeSystem()
				.addEvent(recalculateAveragePriceIndexEvent, -1,
						MonthType.EVERY, DayType.EVERY, HourType.HOUR_15);
		ApplicationContext
				.getInstance()
				.getTimeSystem()
				.addEvent(recalculateAveragePriceIndexEvent, -1,
						MonthType.EVERY, DayType.EVERY, HourType.HOUR_21);

		// recalculate key interest rate every day
		final TimeSystemEvent keyInterestRateCalculationEvent = new KeyInterestRateCalculationEvent();
		this.timeSystemEvents.add(keyInterestRateCalculationEvent);
		ApplicationContext
				.getInstance()
				.getTimeSystem()
				.addEvent(keyInterestRateCalculationEvent, -1, MonthType.EVERY,
						DayType.EVERY, HourType.HOUR_01);

		// count number of snapshots that are taken per day
		int numberOfSnapshotsPerDay = 0;
		for (TimeSystemEvent event : CentralBankImpl.this.timeSystemEvents)
			if (event instanceof MarginalPriceSnapshotEvent)
				numberOfSnapshotsPerDay++;
		this.NUMBER_OF_MARGINAL_PRICE_SNAPSHOTS_PER_DAY = numberOfSnapshotsPerDay;

		// statistical office; has to be initialized after calculating
		// NUMBER_OF_SNAPSHOTS_PER_DAY
		this.statisticalOffice = new StatisticalOffice();
	}

	@Override
	public void deconstruct() {
		super.deconstruct();

		ApplicationContext.getInstance().getCentralBankFactory()
				.deleteCentralBank(this);
	}

	/*
	 * accessors
	 */

	public BankAccount getBankAccountCentralBankMoney() {
		return bankAccountCentralBankMoney;
	}

	public double getEffectiveKeyInterestRate() {
		return this.effectiveKeyInterestRate;
	}

	public Currency getPrimaryCurrency() {
		return this.primaryCurrency;
	}

	public void setBankAccountCentralBankMoney(
			BankAccount bankAccountCentralBankMoney) {
		this.bankAccountCentralBankMoney = bankAccountCentralBankMoney;
	}

	public void setEffectiveKeyInterestRate(double effectiveKeyInterestRate) {
		this.effectiveKeyInterestRate = effectiveKeyInterestRate;
	}

	public void setPrimaryCurrency(final Currency primaryCurrency) {
		this.primaryCurrency = primaryCurrency;
	}

	/*
	 * assertions
	 */

	@Transient
	protected void assertCurrencyIsOffered(Currency currency) {
		assert (this.primaryCurrency == currency);
	}

	@Transient
	@Override
	public void assureBankAccountTransactions() {
		if (this.isDeconstructed)
			return;

		if (this.bankAccountTransactions == null) {
			/*
			 * initialize the banks own bank account and open a customer account
			 * at this new bank, so that this bank can transfer money from its
			 * own bank account
			 */
			this.bankAccountTransactions = this.getPrimaryBank()
					.openBankAccount(this, this.primaryCurrency, true,
							"transactions", TermType.SHORT_TERM,
							MoneyType.DEPOSITS);
		}
	}

	@Transient
	public void assureBankAccountCentralBankMoney() {
		if (this.isDeconstructed)
			return;

		if (this.bankAccountCentralBankMoney == null) {
			/*
			 * initialize the banks own bank account and open a customer account
			 * at this new bank, so that this bank can transfer money from its
			 * own bank account
			 */
			this.bankAccountCentralBankMoney = this.getPrimaryBank()
					.openBankAccount(this, this.primaryCurrency, true,
							"central bank money", TermType.LONG_TERM,
							MoneyType.CENTRALBANK_MONEY);
		}
	}

	/*
	 * business logic
	 */

	@Transient
	public void closeCustomerAccount(BankCustomer customer) {
		this.assureBankAccountCentralBankMoney();

		// each customer bank account ...
		for (BankAccount bankAccount : ApplicationContext.getInstance()
				.getBankAccountDAO().findAll(this, customer)) {
			// on closing has to be evened up to 0, so that no money is
			// lost in the monetary system
			switch (bankAccount.getMoneyType()) {
			case DEPOSITS:
				if (this.bankAccountTransactions != null
						&& bankAccount != this.bankAccountTransactions) {
					if (bankAccount.getBalance() >= 0) {
						this.transferMoney(bankAccount,
								this.bankAccountTransactions,
								bankAccount.getBalance(),
								"evening-up of closed bank account", true);
					} else {
						this.transferMoney(this.bankAccountTransactions,
								bankAccount, -1.0 * bankAccount.getBalance(),
								"evening-up of closed bank account", true);
					}
				}
				break;
			case CENTRALBANK_MONEY:
				if (this.bankAccountCentralBankMoney != null
						&& bankAccount != this.bankAccountCentralBankMoney) {

					if (bankAccount.getBalance() >= 0) {
						this.transferMoney(bankAccount,
								this.bankAccountCentralBankMoney,
								bankAccount.getBalance(),
								"evening-up of closed bank account", true);
					} else {
						this.transferMoney(this.bankAccountCentralBankMoney,
								bankAccount, -1.0 * bankAccount.getBalance(),
								"evening-up of closed bank account", true);
					}
				}
				break;
			}
			customer.onBankCloseBankAccount(bankAccount);
		}
		ApplicationContext.getInstance().getBankAccountFactory()
				.deleteAllBankAccounts(this, customer);
	}

	@Transient
	public double getAverageMarginalPriceForGoodType(GoodType goodType) {
		return this.statisticalOffice
				.getAverageMarginalPriceForGoodType(goodType);
	}

	@Transient
	public BankAccountDelegate getBankAccountCentralBankMoneyDelegate() {
		final BankAccountDelegate delegate = new BankAccountDelegate() {
			@Override
			public BankAccount getBankAccount() {
				CentralBankImpl.this.assureBankAccountCentralBankMoney();
				return CentralBankImpl.this.bankAccountCentralBankMoney;
			}

			@Override
			public void onTransfer(final double amount) {
			}
		};
		return delegate;
	}

	@Transient
	public double getReserveRatio() {
		return ApplicationContext.getInstance().getConfiguration().centralBankConfig
				.getReserveRatio();
	}

	@Override
	@Transient
	protected BalanceSheetDTO issueBalanceSheet() {
		this.assureBankAccountCentralBankMoney();

		final BalanceSheetDTO balanceSheet = super.issueBalanceSheet();

		// bank account for interactions with central bank money accounts of
		// credit banks
		balanceSheet.addBankAccountBalance(this.bankAccountCentralBankMoney);

		return balanceSheet;
	}

	@Transient
	public void obtainTender(final BankAccount moneyReservesBankAccount,
			final List<FixedRateBond> bonds) {
		this.assureBankAccountCentralBankMoney();

		this.assertIsCustomerOfThisBank(moneyReservesBankAccount.getOwner());

		for (FixedRateBond bond : bonds) {
			// bank money creation; fiat money!
			assert (MoneyType.CENTRALBANK_MONEY.equals(moneyReservesBankAccount
					.getMoneyType()));

			// transfer money
			moneyReservesBankAccount.deposit(bond.getFaceValue());

			// transfer bond
			ApplicationContext.getInstance().getPropertyService()
					.transferProperty(bond, bond.getOwner(), this);

			assert (bond.getOwner() == this);

			bond.setFaceValueToBankAccountDelegate(getBankAccountCentralBankMoneyDelegate());
			bond.setCouponToBankAccountDelegate(getBankAccountCentralBankMoneyDelegate());

			if (getLog().isAgentSelectedByClient(
					moneyReservesBankAccount.getOwner()))
				getLog().log(
						moneyReservesBankAccount.getOwner(),
						"obtained a tender of "
								+ Currency.formatMoneySum(bond.getFaceValue())
								+ " " + this.getPrimaryCurrency()
								+ " of central bank money from " + this);
		}
	}

	@Override
	@Transient
	public void onBankCloseBankAccount(BankAccount bankAccount) {
		if (this.bankAccountCentralBankMoney != null
				&& this.bankAccountCentralBankMoney == bankAccount) {
			this.bankAccountCentralBankMoney = null;
		}

		super.onBankCloseBankAccount(bankAccount);
	}

	@Transient
	public void transferMoney(BankAccount from, BankAccount to, double amount,
			String subject) {
		this.transferMoney(from, to, amount, subject, false);
	}

	@Transient
	protected void transferMoney(BankAccount from, BankAccount to,
			double amount, String subject, boolean negativeAmountOK) {

		assert (amount >= 0.0 || negativeAmountOK);
		assert (from != null);
		assert (to != null);
		assert (from != to);

		final double fromBalanceBefore = from.getBalance();
		final double toBalanceBefore = to.getBalance();

		this.assertIdenticalMoneyType(from, to);

		if (from.getManagingBank() instanceof CentralBankImpl
				&& to.getManagingBank() instanceof CentralBankImpl) {
			getLog().bank_onTransfer(from, to, from.getCurrency(), amount,
					subject);
			this.transferMoneyInternally(from, to, amount);
		} else if (from.getManagingBank() instanceof CreditBank
				&& to.getManagingBank() instanceof CentralBankImpl)
			this.transferMoneyFromCreditBankAccountToCentralBankAccount(from,
					to, amount);
		else if (from.getManagingBank() instanceof CentralBankImpl
				&& to.getManagingBank() instanceof CreditBank) {
			this.transferMoneyFromCentralBankAccountToCreditBankAccount(from,
					to, amount);
		} else
			throw new RuntimeException("uncovered case");

		assert (fromBalanceBefore - amount == from.getBalance());
		assert (toBalanceBefore + amount == to.getBalance());
	}

	@Transient
	private void transferMoneyInternally(BankAccount from, BankAccount to,
			double amount) {
		this.assertBankAccountIsManagedByThisBank(from);
		this.assertBankAccountIsManagedByThisBank(to);

		assert (amount >= 0);
		assert (from.getCurrency().equals(to.getCurrency()));
		// unusual at the central bank
		assert (from.getBalance() - amount >= 0 || from.getOverdraftPossible());

		final double fromBalanceBefore = from.getBalance();
		final double toBalanceBefore = to.getBalance();

		// transfer money internally
		from.withdraw(amount);
		to.deposit(amount);

		// from and to can be the same
		assert (MathUtil.equal(fromBalanceBefore + toBalanceBefore,
				from.getBalance() + to.getBalance()));
	}

	@Transient
	private void transferMoneyFromCentralBankAccountToCreditBankAccount(
			BankAccount from, BankAccount to, double amount) {
		/*
		 * Checks
		 */
		this.assertBankAccountIsManagedByThisBank(from);

		assert (amount >= 0.0);
		assert (from.getCurrency().equals(to.getCurrency()));

		// unusual at the central bank
		assert (from.getBalance() - amount >= 0.0 || from
				.getOverdraftPossible());

		assert (to.getManagingBank() instanceof CreditBank);

		/*
		 * Transaction
		 */
		final double fromBalanceBefore = from.getBalance();
		final double toBalanceBefore = to.getBalance();

		CreditBank creditBank = (CreditBank) to.getManagingBank();
		from.withdraw(amount);
		creditBank.deposit(to, amount);

		assert (fromBalanceBefore - amount == from.getBalance());
		assert (toBalanceBefore + amount == to.getBalance());
	}

	@Transient
	private void transferMoneyFromCreditBankAccountToCentralBankAccount(
			BankAccount from, BankAccount to, double amount) {
		/*
		 * Checks
		 */
		this.assertBankAccountIsManagedByThisBank(to);

		assert (amount >= 0);
		assert (from.getCurrency().equals(to.getCurrency()));
		// unusual at the central bank
		assert (from.getBalance() - amount >= 0 || from.getOverdraftPossible());
		assert (from.getManagingBank() instanceof CreditBank);

		/*
		 * Transaction
		 */
		final double fromBalanceBefore = from.getBalance();
		final double toBalanceBefore = to.getBalance();

		CreditBank creditBank = (CreditBank) from.getManagingBank();
		creditBank.withdraw(from, amount);
		to.deposit(amount);

		assert (fromBalanceBefore - amount == from.getBalance());
		assert (toBalanceBefore + amount == to.getBalance());
	}

	public class DailyInterestCalculationEvent implements TimeSystemEvent {
		@Override
		public void onEvent() {
			CentralBankImpl.this.assureBankAccountTransactions();

			final double monthlyInterestRate = MathUtil
					.calculateMonthlyNominalInterestRate(CentralBankImpl.this.effectiveKeyInterestRate);
			final double dailyInterestRate = monthlyInterestRate / 30.0;

			for (BankAccount bankAccount : ApplicationContext.getInstance()
					.getBankAccountDAO()
					.findAllBankAccountsManagedByBank(CentralBankImpl.this)) {
				if (bankAccount.getOwner() != CentralBankImpl.this) {
					assert (CentralBankImpl.this.primaryCurrency
							.equals(bankAccount.getCurrency()));

					final double dailyInterest = bankAccount.getBalance()
							* dailyInterestRate;

					// liability account & positive interest rate or asset
					// account &
					// negative interest rate
					if (dailyInterest > 0.0) {
						CentralBankImpl.this.transferMoneyInternally(
								CentralBankImpl.this.bankAccountTransactions,
								bankAccount, dailyInterest);
					}
					// asset account & positive interest rate or liability
					// account & negative interest rate
					else if (dailyInterest < 0.0) {
						final double absDailyInterest = -1.0 * dailyInterest;
						CentralBankImpl.this.transferMoneyInternally(
								bankAccount,
								CentralBankImpl.this.bankAccountTransactions,
								absDailyInterest);
					}
				}
			}

			// profits are transferred to the state, instead of dividends to
			// share holders etc.
			if (CentralBankImpl.this.bankAccountTransactions.getBalance() > 0.0) {
				State state = ApplicationContext.getInstance().getStateDAO()
						.findByCurrency(primaryCurrency);
				CentralBankImpl.this.transferMoney(
						CentralBankImpl.this.bankAccountTransactions, state
								.getBankAccountTransactionsDelegate()
								.getBankAccount(),
						CentralBankImpl.this.bankAccountTransactions
								.getBalance(), "national interest");
			}
		}
	}

	public class MarginalPriceSnapshotEvent implements TimeSystemEvent {
		@Override
		public void onEvent() {
			CentralBankImpl.this.statisticalOffice
					.takeSnapshotOfMarginalPrices();
		}
	}

	public class KeyInterestRateCalculationEvent implements TimeSystemEvent {
		@Override
		public void onEvent() {
			// calculate price index
			CentralBankImpl.this.statisticalOffice.recalculateAveragePrices();
			CentralBankImpl.this.statisticalOffice.recalculatePriceIndex();
			double priceIndex = CentralBankImpl.this.statisticalOffice
					.getPriceIndex();
			getLog().centralBank_PriceIndex(
					CentralBankImpl.this.primaryCurrency, priceIndex);

			// calculate key interest rate
			CentralBankImpl.this.effectiveKeyInterestRate = calculateEffectiveKeyInterestRate();
			getLog().centralBank_KeyInterestRate(
					CentralBankImpl.this.primaryCurrency,
					CentralBankImpl.this.effectiveKeyInterestRate);
		}

		@Transient
		protected double calculateEffectiveKeyInterestRate() {
			double targetPriceIndexForCurrentPeriod = this
					.calculateTargetPriceIndexForPeriod();
			double currentPriceIndex = CentralBankImpl.this.statisticalOffice
					.getPriceIndex();
			double newEffectiveKeyInterestRate = 0.03 + (((currentPriceIndex - targetPriceIndexForCurrentPeriod) / currentPriceIndex) / 10.0);

			if (!Double.isNaN(newEffectiveKeyInterestRate)
					&& !Double.isInfinite(newEffectiveKeyInterestRate)) {
				if (ApplicationContext.getInstance().getConfiguration().centralBankConfig
						.getAllowNegativeKeyInterestRate()) {
					return newEffectiveKeyInterestRate;
				} else {
					return Math.max(0.0, newEffectiveKeyInterestRate);
				}
			} else {
				return CentralBankImpl.this.effectiveKeyInterestRate;
			}
		}

		@Transient
		protected double calculateTargetPriceIndexForPeriod() {
			int yearNumber = ApplicationContext.getInstance().getTimeSystem()
					.getCurrentYear()
					- ApplicationContext.getInstance().getTimeSystem()
							.getStartYear();
			double targetPriceLevelForYear = Math
					.pow((1.0 + ApplicationContext.getInstance()
							.getConfiguration().centralBankConfig
							.getInflationTarget()), yearNumber);

			double monthlyNominalInflationTarget = MathUtil
					.calculateMonthlyNominalInterestRate(ApplicationContext
							.getInstance().getConfiguration().centralBankConfig
							.getInflationTarget());

			double targetPriceLevelForMonth = Math.pow(
					1.0 + monthlyNominalInflationTarget,
					(double) ApplicationContext.getInstance().getTimeSystem()
							.getCurrentMonthNumberInYear() - 1.0) - 1.0;
			double targetPriceLevelForDay = (monthlyNominalInflationTarget / 30.0)
					* ApplicationContext.getInstance().getTimeSystem()
							.getCurrentDayNumberInMonth();

			double combinedTargetPriceLevel = (targetPriceLevelForYear
					+ targetPriceLevelForMonth + targetPriceLevelForDay);
			return ApplicationContext.getInstance().getConfiguration().centralBankConfig
					.getTargetPriceIndex() * combinedTargetPriceLevel;
		}
	}

	/**
	 * the statistical office takes snapshots of marginal market prices not only
	 * for the purpose of calculating the price index, but generally for
	 * offering information about markets to agents
	 */
	protected class StatisticalOffice {
		// constants

		protected final int NUMBER_OF_LOGGED_PERIODS = 3;

		protected final Map<GoodType, Double> priceIndexWeights = new HashMap<GoodType, Double>();

		// state

		protected Map<GoodType, double[]> monitoredMarginalPricesForGoodTypesAndPeriods = new HashMap<GoodType, double[]>();

		// pre-calculated values

		protected Map<GoodType, Double> averageMarginalPricesForGoodTypes = new HashMap<GoodType, Double>();

		protected double priceIndex = 0.0;

		public StatisticalOffice() {
			/*
			 * set price index weights, must sum up to 1.0
			 */

			double priceIndexWeightSum = 0.0;
			for (GoodType goodType : GoodType.values()) {
				double priceIndexWeight = ApplicationContext.getInstance()
						.getConfiguration().centralBankConfig.statisticalOfficeConfig
						.getPriceIndexWeight(goodType);
				this.priceIndexWeights.put(goodType, priceIndexWeight);
				priceIndexWeightSum += priceIndexWeight;
			}

			assert (priceIndexWeightSum == 1.0);

			/*
			 * GoodType LABOURHOUR is not monitored, as its market price is not
			 * influenced by the key interest rate over a transmission mechanism
			 * in the buying behaviour, but instead by comparison of marginal
			 * costs and prices in the production behaviour
			 */

			/*
			 * initialize monitoredMarginalPrices and
			 * averageMarginalPricesForGoodTypes; prices should be stored for
			 * all GoodTypes, not only those in the price index
			 */
			for (GoodType goodType : GoodType.values()) {
				this.monitoredMarginalPricesForGoodTypesAndPeriods
						.put(goodType,
								new double[NUMBER_OF_LOGGED_PERIODS
										* CentralBankImpl.this.NUMBER_OF_MARGINAL_PRICE_SNAPSHOTS_PER_DAY]);

				this.averageMarginalPricesForGoodTypes
						.put(goodType, Double.NaN);
			}
		}

		protected void takeSnapshotOfMarginalPrices() {
			// store marginal prices of monitored good types for this period
			for (Entry<GoodType, double[]> entry : this.monitoredMarginalPricesForGoodTypesAndPeriods
					.entrySet()) {
				double[] pricesForGoodType = entry.getValue();

				// fetch and store current price for this good type
				double marginalPriceForGoodType = ApplicationContext
						.getInstance()
						.getMarketService()
						.getPrice(CentralBankImpl.this.primaryCurrency,
								entry.getKey());

				if (!Double.isNaN(marginalPriceForGoodType)
						&& !Double.isInfinite(marginalPriceForGoodType)) {
					// shift prices of older periods for this good type
					System.arraycopy(pricesForGoodType, 0, pricesForGoodType,
							1, pricesForGoodType.length - 1);
					pricesForGoodType[0] = marginalPriceForGoodType;
				}
			}
		}

		protected void recalculateAveragePrices() {
			// for each monitored good type
			for (Entry<GoodType, double[]> entry : this.monitoredMarginalPricesForGoodTypesAndPeriods
					.entrySet()) {
				double[] monitoredMarginalPricesForGoodType = entry.getValue();

				double priceSumForGoodType = 0.0;
				double totalWeight = 0;

				// recalculate average price
				for (int i = 0; i < monitoredMarginalPricesForGoodType.length; i++) {
					double marginalPriceForGoodType = monitoredMarginalPricesForGoodType[i];
					if (marginalPriceForGoodType != 0.0
							&& !Double.isNaN(marginalPriceForGoodType)
							&& !Double.isInfinite(marginalPriceForGoodType)) {
						// weight period by age
						double weight = monitoredMarginalPricesForGoodType.length
								- i;
						priceSumForGoodType += marginalPriceForGoodType
								* weight;
						totalWeight += weight;
					}
				}

				double averagePriceForGoodType = priceSumForGoodType
						/ (double) totalWeight;
				if (!Double.isNaN(totalWeight)
						&& !Double.isInfinite(totalWeight))
					this.averageMarginalPricesForGoodTypes.put(entry.getKey(),
							averagePriceForGoodType);
			}
		}

		protected void recalculatePriceIndex() {
			double newPriceIndex = Double.NaN;

			// for basket of good types
			for (Entry<GoodType, Double> entry : this.priceIndexWeights
					.entrySet()) {
				GoodType goodType = entry.getKey();
				Double weight = entry.getValue();

				// average marginal price of the good type
				double averageMarginalPrice = this.averageMarginalPricesForGoodTypes
						.get(goodType);

				// add marginal price for good type to price index, weighted by
				// defined weight
				double newPriceIndexForGoodType = weight * averageMarginalPrice;

				if (!Double.isNaN(newPriceIndexForGoodType)
						&& !Double.isInfinite(newPriceIndexForGoodType)) {
					if (Double.isNaN(newPriceIndex)
							|| Double.isInfinite(newPriceIndex))
						newPriceIndex = newPriceIndexForGoodType;
					else
						newPriceIndex += newPriceIndexForGoodType;
				}
			}

			// store average price index
			this.priceIndex = newPriceIndex;
		}

		protected double getPriceIndex() {
			return this.priceIndex;
		}

		protected double getAverageMarginalPriceForGoodType(GoodType goodType) {
			return this.averageMarginalPricesForGoodTypes.get(goodType);
		}
	}

}