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

package compecon.economy.markets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.SortedMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import compecon.CompEconTestSupport;
import compecon.economy.markets.Market.MarketPriceFunction;
import compecon.economy.markets.ordertypes.MarketOrder;
import compecon.economy.sectors.financial.CreditBank;
import compecon.economy.sectors.financial.Currency;
import compecon.economy.sectors.household.Household;
import compecon.economy.sectors.industry.Factory;
import compecon.economy.sectors.state.law.property.PropertyRegister;
import compecon.economy.sectors.state.law.security.equity.JointStockCompany;
import compecon.economy.sectors.state.law.security.equity.Share;
import compecon.economy.sectors.trading.Trader;
import compecon.engine.MarketFactory;
import compecon.engine.dao.DAOFactory;
import compecon.engine.time.ITimeSystemEvent;
import compecon.materia.GoodType;
import compecon.math.price.IPriceFunction.PriceFunctionConfig;

public class MarketTest extends CompEconTestSupport {

	@Before
	public void setUp() {
		super.setUp();
	}

	@After
	public void tearDown() {
		super.tearDown();
	}

	@Test
	public void testOfferGoodType() {
		// test market for good type
		Currency currency = Currency.EURO;
		GoodType goodType = GoodType.LABOURHOUR;

		Household household1_EUR = DAOFactory.getHouseholdDAO()
				.findAllByCurrency(currency).get(0);
		Household household2_EUR = DAOFactory.getHouseholdDAO()
				.findAllByCurrency(currency).get(1);
		Factory factory1_WHEAT_EUR = DAOFactory.getFactoryDAO()
				.findAllByCurrency(currency).get(0);

		assertEquals(Double.NaN,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);

		MarketFactory.getInstance().placeSellingOffer(goodType, household1_EUR,
				household1_EUR.getTransactionsBankAccount(), 10, 5);
		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 10, 4);

		assertEquals(4.0,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);
		assertEquals(
				MarketFactory.getInstance().getPrice(currency, goodType),
				MarketFactory.getInstance()
						.getMarketPriceFunction(currency, goodType)
						.getPrice(0.0), epsilon);
		assertEquals(
				MarketFactory.getInstance().getPrice(currency, goodType),
				MarketFactory.getInstance()
						.getMarketPriceFunction(currency, goodType)
						.getPrice(1.0), epsilon);

		assertEquals(
				4.5,
				MarketFactory.getInstance()
						.getMarketPriceFunction(currency, goodType)
						.getPrice(20.0), epsilon);
		assertEquals(4.333333, MarketFactory.getInstance()
				.getMarketPriceFunction(currency, goodType).getPrice(15.0),
				epsilon);
		assertEquals(
				4.0,
				MarketFactory.getInstance()
						.getMarketPriceFunction(currency, goodType)
						.getMarginalPrice(10.0), epsilon);
		assertEquals(
				5.0,
				MarketFactory.getInstance()
						.getMarketPriceFunction(currency, goodType)
						.getMarginalPrice(11.0), epsilon);
		assertEquals(
				Double.NaN,
				MarketFactory.getInstance()
						.getMarketPriceFunction(currency, goodType)
						.getMarginalPrice(21.0), epsilon);
		assertEquals(
				Double.NaN,
				MarketFactory.getInstance().getAveragePrice(currency, goodType,
						21.0), epsilon);

		MarketFactory.getInstance().removeAllSellingOffers(household2_EUR);
		assertEquals(5.0,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);

		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 10, 3);
		assertEquals(3.0,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);

		MarketFactory.getInstance().removeAllSellingOffers(household2_EUR,
				currency, goodType);
		assertEquals(5.0,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);

		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 10, 3);
		assertEquals(3.0,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);

		SortedMap<MarketOrder, Double> marketOffers1 = MarketFactory
				.getInstance().findBestFulfillmentSet(currency, 20, Double.NaN,
						3, goodType);
		assertEquals(1, marketOffers1.size());

		SortedMap<MarketOrder, Double> marketOffers2 = MarketFactory
				.getInstance().findBestFulfillmentSet(currency, 20, Double.NaN,
						5, goodType);
		assertEquals(2, marketOffers2.size());

		MarketFactory.getInstance().buy(goodType, 5, Double.NaN, 8,
				factory1_WHEAT_EUR,
				factory1_WHEAT_EUR.getTransactionsBankAccount());

		assertEquals(
				5,
				PropertyRegister.getInstance().getBalance(factory1_WHEAT_EUR,
						goodType), epsilon);
		assertEquals(-15.0, factory1_WHEAT_EUR.getTransactionsBankAccount()
				.getBalance(), epsilon);
	}

	@Test
	public void testOfferProperty() {
		Currency currency = Currency.EURO;

		Factory factory1_WHEAT_EUR = DAOFactory.getFactoryDAO()
				.findAllByCurrency(currency).get(0);
		Household household1_EUR = DAOFactory.getHouseholdDAO()
				.findAllByCurrency(currency).get(0);

		assertEquals(Double.NaN,
				MarketFactory.getInstance().getPrice(currency, Share.class),
				epsilon);

		for (ITimeSystemEvent timeSystemEvent : factory1_WHEAT_EUR
				.getTimeSystemEvents()) {
			if (timeSystemEvent instanceof JointStockCompany.OfferSharesEvent)
				timeSystemEvent.onEvent();
		}

		assertEquals(0.0,
				MarketFactory.getInstance().getPrice(currency, Share.class),
				epsilon);
		assertEquals(
				factory1_WHEAT_EUR.getInitialNumberOfShares(),
				PropertyRegister.getInstance()
						.getProperties(factory1_WHEAT_EUR, Share.class).size());

		MarketFactory.getInstance().buy(Share.class, 1, Double.NaN, Double.NaN,
				household1_EUR, household1_EUR.getTransactionsBankAccount());

		assertEquals(
				factory1_WHEAT_EUR.getInitialNumberOfShares() - 1,
				PropertyRegister.getInstance()
						.getProperties(factory1_WHEAT_EUR, Share.class).size());
		assertEquals(
				1,
				PropertyRegister.getInstance()
						.getProperties(household1_EUR, Share.class).size());

		MarketFactory.getInstance().removeAllSellingOffers(factory1_WHEAT_EUR);
		assertEquals(Double.NaN,
				MarketFactory.getInstance().getPrice(currency, Share.class),
				epsilon);
	}

	@Test
	public void testOfferCurrency() {
		Currency currency = Currency.EURO;
		Currency commodityCurrency = Currency.USDOLLAR;

		CreditBank creditBank1_EUR = DAOFactory.getCreditBankDAO()
				.findAllByCurrency(currency).get(0);
		CreditBank creditBank2_EUR = DAOFactory.getCreditBankDAO()
				.findAllByCurrency(currency).get(1);
		Trader trader1_EUR = DAOFactory.getTraderDAO()
				.findAllByCurrency(currency).get(0);

		assertEquals(
				Double.NaN,
				MarketFactory.getInstance().getPrice(currency,
						commodityCurrency), epsilon);

		MarketFactory.getInstance().placeSellingOffer(
				commodityCurrency,
				creditBank1_EUR,
				creditBank1_EUR.getTransactionsBankAccount(),
				10,
				2,
				creditBank1_EUR.getCurrencyTradeBankAccounts().get(
						commodityCurrency));

		MarketFactory.getInstance().placeSellingOffer(
				commodityCurrency,
				creditBank2_EUR,
				creditBank2_EUR.getTransactionsBankAccount(),
				10,
				3,
				creditBank2_EUR.getCurrencyTradeBankAccounts().get(
						commodityCurrency));
		assertEquals(
				2.0,
				MarketFactory.getInstance().getPrice(currency,
						commodityCurrency), epsilon);

		MarketFactory.getInstance().removeAllSellingOffers(creditBank1_EUR);
		assertEquals(
				3,
				MarketFactory.getInstance().getPrice(currency,
						commodityCurrency), epsilon);

		MarketFactory.getInstance().placeSellingOffer(
				commodityCurrency,
				creditBank1_EUR,
				creditBank1_EUR.getTransactionsBankAccount(),
				10,
				1,
				creditBank1_EUR.getCurrencyTradeBankAccounts().get(
						commodityCurrency));
		assertEquals(
				1.0,
				MarketFactory.getInstance().getPrice(currency,
						commodityCurrency), epsilon);

		MarketFactory.getInstance().removeAllSellingOffers(creditBank1_EUR,
				currency, commodityCurrency);
		assertEquals(
				3.0,
				MarketFactory.getInstance().getPrice(currency,
						commodityCurrency), epsilon);

		MarketFactory.getInstance().placeSellingOffer(
				commodityCurrency,
				creditBank1_EUR,
				creditBank1_EUR.getTransactionsBankAccount(),
				10,
				1,
				creditBank1_EUR.getCurrencyTradeBankAccounts().get(
						commodityCurrency));
		assertEquals(
				1.0,
				MarketFactory.getInstance().getPrice(currency,
						commodityCurrency), epsilon);

		SortedMap<MarketOrder, Double> marketOffers1 = MarketFactory
				.getInstance().findBestFulfillmentSet(currency, 20, Double.NaN,
						1, commodityCurrency);
		assertEquals(1, marketOffers1.size());

		SortedMap<MarketOrder, Double> marketOffers2 = MarketFactory
				.getInstance().findBestFulfillmentSet(currency, 20, Double.NaN,
						5, commodityCurrency);
		assertEquals(2, marketOffers2.size());

		MarketFactory.getInstance().buy(
				commodityCurrency,
				5,
				Double.NaN,
				8,
				trader1_EUR,
				trader1_EUR.getTransactionsBankAccount(),
				trader1_EUR.getTransactionForeignCurrencyAccounts().get(
						commodityCurrency));

		assertEquals(-5.0, trader1_EUR.getTransactionsBankAccount()
				.getBalance(), epsilon);
		assertEquals(5.0, trader1_EUR.getTransactionForeignCurrencyAccounts()
				.get(commodityCurrency).getBalance(), epsilon);
	}

	@Test
	public void testCalculateMarketPriceFunction() {
		Currency currency = Currency.EURO;
		GoodType goodType = GoodType.LABOURHOUR;

		Household household1_EUR = DAOFactory.getHouseholdDAO()
				.findAllByCurrency(currency).get(0);
		Household household2_EUR = DAOFactory.getHouseholdDAO()
				.findAllByCurrency(currency).get(1);

		assertEquals(Double.NaN,
				MarketFactory.getInstance().getPrice(currency, goodType),
				epsilon);

		MarketFactory.getInstance().placeSellingOffer(goodType, household1_EUR,
				household1_EUR.getTransactionsBankAccount(), 10, 5);
		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 10, 4);
		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 10, 6);

		assertValidPriceFunctionConfig(MarketFactory.getInstance()
				.getMarketPriceFunction(currency, goodType), 150.0, 3);

		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 100, 2);
		MarketFactory.getInstance().placeSellingOffer(goodType, household2_EUR,
				household2_EUR.getTransactionsBankAccount(), 20, 20);

		assertValidPriceFunctionConfig(MarketFactory.getInstance()
				.getMarketPriceFunction(currency, goodType), 1500.0, 5);
	}

	private void assertValidPriceFunctionConfig(
			MarketPriceFunction marketPriceFunction, double maxBudget,
			int numberOfOffers) {
		PriceFunctionConfig[] priceFunctionConfigs = marketPriceFunction
				.getAnalyticalPriceFunctionParameters(maxBudget);
		assertEquals(numberOfOffers, priceFunctionConfigs.length);

		// check intervals
		double lastPriceAtIntervalRightBoundary = 0.0;
		for (PriceFunctionConfig priceFunctionConfig : priceFunctionConfigs) {
			// check interval boundaries
			assertNotEquals(priceFunctionConfig.intervalLeftBoundary,
					priceFunctionConfig.intervalRightBoundary, epsilon);

			double intervalMiddle = priceFunctionConfig.intervalRightBoundary
					- ((priceFunctionConfig.intervalRightBoundary - priceFunctionConfig.intervalLeftBoundary) / 2.0);

			// calculate analytical prices
			double priceAtIntervalRightBoundary = priceFunctionConfig.coefficientXPower0
					+ priceFunctionConfig.coefficientXPowerMinus1
					/ priceFunctionConfig.intervalRightBoundary;
			double priceAtIntervalMiddle = priceFunctionConfig.coefficientXPower0
					+ priceFunctionConfig.coefficientXPowerMinus1
					/ intervalMiddle;

			// compare analytical prices with prices from market price function
			assertEquals(priceAtIntervalMiddle,
					marketPriceFunction.getPrice(intervalMiddle), epsilon);
			assertEquals(
					priceAtIntervalRightBoundary,
					marketPriceFunction
							.getPrice(priceFunctionConfig.intervalRightBoundary),
					epsilon);

			if (priceFunctionConfig.intervalLeftBoundary > 0.0) {
				double priceAtIntervalLeftBoundary = priceFunctionConfig.coefficientXPower0
						+ priceFunctionConfig.coefficientXPowerMinus1
						/ priceFunctionConfig.intervalLeftBoundary;

				// assert that the analytical price function does not have
				// discontinuities
				assertEquals(lastPriceAtIntervalRightBoundary,
						priceAtIntervalLeftBoundary, epsilon);

				// assert that the analytical price function is continuous
				assertTrue(priceAtIntervalLeftBoundary < priceAtIntervalMiddle);
				assertTrue(priceAtIntervalMiddle < priceAtIntervalRightBoundary);

				assertEquals(
						priceAtIntervalLeftBoundary,
						marketPriceFunction
								.getPrice(priceFunctionConfig.intervalLeftBoundary),
						epsilon);
			}

			// store the current right interval boundary as the new left
			// interval boundary for the next step of the step price function
			lastPriceAtIntervalRightBoundary = priceAtIntervalRightBoundary;
		}
	}
}