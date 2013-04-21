/*
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

package compecon.engine;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import compecon.culture.sectors.agriculture.Farm;
import compecon.culture.sectors.financial.CentralBank;
import compecon.culture.sectors.financial.CreditBank;
import compecon.culture.sectors.financial.Currency;
import compecon.culture.sectors.household.Household;
import compecon.culture.sectors.industry.Factory;
import compecon.culture.sectors.state.law.bookkeeping.BalanceSheet;
import compecon.culture.sectors.state.law.property.IProperty;
import compecon.engine.dashboard.Dashboard;
import compecon.engine.time.TimeSystem;
import compecon.engine.util.MathUtil;
import compecon.nature.materia.GoodType;

public class Log {

	private static boolean logTransactions = false;

	public static void setLogTransactions(boolean logTransactions) {
		Log.logTransactions = logTransactions;
	}

	public static void notifyTimeSystem_nextDay(Date date) {
		Dashboard.getInstance().getAgentLogsModel()
				.signalizeContentModification();
		Dashboard.getInstance().getBalanceSheetsModel().nextPeriod();
		Dashboard.getInstance().getCapacityModel().nextPeriod();
		Dashboard.getInstance().getMonetaryTransactionsModel().nextPeriod();
		Dashboard.getInstance().getEffectiveProductionOutputModel()
				.nextPeriod();
		Dashboard.getInstance().getMoneySupplyM0Model().nextPeriod();
		Dashboard.getInstance().getMoneySupplyM1Model().nextPeriod();
		Dashboard.getInstance().getUtilityModel().nextPeriod();

		Dashboard.getInstance().nextPeriod();
	}

	// --------

	public static void log(Agent agent, String message) {
		Dashboard
				.getInstance()
				.getAgentLogsModel()
				.logAgentEvent(TimeSystem.getInstance().getCurrentDate(),
						agent, message);
	}

	public static void agent_onConstruct(Agent agent) {
		log(agent, agent + " constructed");
		Dashboard.getInstance().getNumberOfAgentsTableModel()
				.agent_onConstruct(agent.getClass());
	}

	public static void agent_onDeconstruct(Agent agent) {
		log(agent, agent + " deconstructed");

		Dashboard.getInstance().getNumberOfAgentsTableModel()
				.agent_onDeconstruct(agent.getClass());
		Dashboard.getInstance().getAgentLogsModel().agent_onDeconstruct(agent);
		Dashboard.getInstance().getBalanceSheetsModel()
				.notifyAgent_onDeconstruct(agent);
	}

	public static void agent_onPublishBalanceSheet(Agent agent,
			BalanceSheet balanceSheet) {
		Dashboard.getInstance().getBalanceSheetsModel()
				.agent_onPublishBalanceSheet(agent, balanceSheet);
	}

	// --------

	public static void household_onUtility(Household household,
			Currency currency, Map<GoodType, Double> bundleOfGoodsToConsume,
			double utility) {

		String log = household + " consumed ";
		int i = 0;
		for (Entry<GoodType, Double> entry : bundleOfGoodsToConsume.entrySet()) {
			log += MathUtil.round(entry.getValue()) + " " + entry.getKey();
			if (i < bundleOfGoodsToConsume.size() - 1)
				log += ", ";
			i++;
		}
		log += " -> " + MathUtil.round(utility) + " utility";

		log(household, log);
		Dashboard.getInstance().getUtilityModel().add(currency, utility);
	}

	public static void household_onConsumeGoods(Household household,
			double consumedAmount, GoodType goodType) {
		log(household,
				household + " consumed " + MathUtil.round(consumedAmount) + " "
						+ goodType);
	}

	public static void household_NotEnoughUtility(Household household,
			double requiredUtility) {
		log(household, household + " does not have required utility of "
				+ requiredUtility);
	}

	public static void household_LabourHourCapacity(Household household,
			double labourHourCapacity) {
		Dashboard.getInstance().getCapacityModel()
				.add(GoodType.LABOURHOUR, labourHourCapacity);
	}

	// --------

	public static void farm_onProduction(Farm farm, double harvestedMegaCalories) {
		log(farm, farm + " produced " + harvestedMegaCalories + " "
				+ GoodType.MEGACALORIE);

		Dashboard.getInstance().getEffectiveProductionOutputModel()
				.add(GoodType.MEGACALORIE, harvestedMegaCalories);
	}

	public static void farm_ProductionCapacity(Farm farm,
			double productionCapacity) {
		log(farm, farm + " has production capacity " + productionCapacity
				+ " for " + GoodType.MEGACALORIE);

		Dashboard.getInstance().getCapacityModel()
				.add(GoodType.MEGACALORIE, productionCapacity);
	}

	public static void farm_onLabourHourExhaust(Farm farm, double amount) {
		Dashboard.getInstance().getEffectiveProductionOutputModel()
				.add(GoodType.LABOURHOUR, amount);
	}

	// --------

	public static void factory_onProduction(Factory factory, GoodType goodType,
			double producedProducts) {
		log(factory, factory + " produced " + producedProducts + " " + goodType);

		Dashboard.getInstance().getEffectiveProductionOutputModel()
				.add(goodType, producedProducts);
	}

	public static void factory_ProductionCapacity(Factory factory,
			GoodType goodType, double productionCapacity) {
		log(factory, factory + " has production capacity " + productionCapacity
				+ " for " + goodType);

		Dashboard.getInstance().getCapacityModel()
				.add(goodType, productionCapacity);
	}

	public static void factory_onLabourHourExhaust(Factory factory,
			double amount) {
		Dashboard.getInstance().getEffectiveProductionOutputModel()
				.add(GoodType.LABOURHOUR, amount);
	}

	// --------

	public static void bank_onTransfer(Agent from, Agent to, Currency currency,
			double value, String subject) {
		Dashboard
				.getInstance()
				.getMonetaryTransactionsModel()
				.bank_onTransfer(from.getClass(), to.getClass(), currency,
						value);
		if (Log.logTransactions) {
			log(from,
					"transfered " + Currency.round(value) + " "
							+ currency.getIso4217Code() + " to " + to
							+ " for: " + subject);
			log(to,
					"received " + Currency.round(value) + " "
							+ currency.getIso4217Code() + " from " + from
							+ " for: " + subject);
		}
	}

	// --------

	public static void centralBank_onObtainTender(CentralBank centralBank,
			double amount, CreditBank creditBank) {
		log(creditBank,
				creditBank + " obtained a tender of " + Currency.round(amount)
						+ " " + centralBank.getCoveredCurrency()
						+ " of central bank money from " + centralBank);
	}

	public static void centralBank_KeyInterestRate(Currency currency,
			double keyInterestRate) {
		Dashboard.getInstance().getKeyInterestRateModel()
				.add(currency, keyInterestRate);
	}

	public static void centralBank_PriceIndex(Currency currency,
			double priceIndex) {
		Dashboard.getInstance().getPriceIndexModel().add(currency, priceIndex);
	}

	// --------

	public static void market_onTick(double pricePerUnit, IProperty property,
			Currency currency, double amount) {
		Dashboard.getInstance().getPricesModel()
				.market_onTick(pricePerUnit, property, currency, amount);
	}
}