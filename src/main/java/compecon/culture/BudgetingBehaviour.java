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

package compecon.culture;

import compecon.culture.sectors.financial.Currency;
import compecon.engine.Agent;
import compecon.engine.AgentFactory;
import compecon.engine.jmx.Log;

/**
 * This behaviour controls buying decisions.It is injected into an agent (thus
 * compositions instead of inheritance). <br />
 * The key interest rate influences the buying behaviour via a simulated
 * transmission mechanism.
 */
public class BudgetingBehaviour {

	protected final Agent agent;

	protected final double internalRateOfReturn = 0.05;

	protected double lastMaxCredit = Double.NaN;

	public BudgetingBehaviour(Agent agent) {
		this.agent = agent;
	}

	/*
	 * maxCredit defines the maximum additional debt the buyer is going to
	 * accept -> nexus with the monetary sphere
	 */
	public double calculateTransmissionBasedBudgetForPeriod(Currency currency,
			double bankAccountBalance, double referenceCredit) {

		/*
		 * set / adjust reference credit
		 */

		if (Double.isNaN(this.lastMaxCredit)) {
			this.lastMaxCredit = referenceCredit;
		}

		double keyInterestRate = AgentFactory.getInstanceCentralBank(currency)
				.getEffectiveKeyInterestRate();
		lastMaxCredit = lastMaxCredit
				* (1 + ((this.internalRateOfReturn - keyInterestRate) / 50));

		/*
		 * transmission mechanism
		 */

		double creditBasedBudget = Math.max(0, bankAccountBalance
				+ this.lastMaxCredit);

		if (Log.isAgentSelectedByClient(BudgetingBehaviour.this.agent))
			Log.log(BudgetingBehaviour.this.agent,
					Currency.round(creditBasedBudget) + " "
							+ currency.getIso4217Code() + " budget = ("
							+ Currency.round(bankAccountBalance) + " "
							+ currency.getIso4217Code()
							+ " bankAccountBalance + "
							+ Currency.round(lastMaxCredit) + " "
							+ currency.getIso4217Code() + " maxCredit)");
		return creditBasedBudget;
	}
}