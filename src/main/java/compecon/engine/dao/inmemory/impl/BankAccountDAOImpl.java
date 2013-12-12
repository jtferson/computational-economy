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

package compecon.engine.dao.inmemory.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import compecon.economy.sectors.financial.Bank;
import compecon.economy.sectors.financial.BankAccount;
import compecon.economy.sectors.financial.BankCustomer;
import compecon.economy.sectors.financial.Currency;
import compecon.engine.dao.BankAccountDAO;

public class BankAccountDAOImpl extends
		AbstractIndexedInMemoryDAOImpl<BankCustomer, BankAccount> implements
		BankAccountDAO {

	protected Map<Bank, List<BankAccount>> bankAccounts = new HashMap<Bank, List<BankAccount>>();

	/*
	 * helpers
	 */

	private void assureInitializedDataStructure(Bank bank) {
		if (!this.bankAccounts.containsKey(bank)) {
			this.bankAccounts.put(bank, new ArrayList<BankAccount>());
		}
	}

	/*
	 * actions
	 */

	@Override
	public synchronized void delete(BankAccount bankAccount) {
		if (this.bankAccounts.containsKey(bankAccount.getManagingBank())) {
			this.bankAccounts.get(bankAccount.getManagingBank()).remove(
					bankAccount);
		}
		super.delete(bankAccount);
	}

	@Override
	public synchronized void deleteAllBankAccounts(Bank managingBank) {
		if (this.bankAccounts.containsKey(managingBank)) {
			for (BankAccount bankAccount : new HashSet<BankAccount>(
					this.bankAccounts.get(managingBank))) {
				this.delete(bankAccount);
			}
		}
		this.bankAccounts.remove(managingBank);
	}

	@Override
	public synchronized void deleteAllBankAccounts(Bank managingBank,
			BankCustomer owner) {
		if (this.bankAccounts.containsKey(managingBank)) {
			final List<BankAccount> bankAccounts = this
					.getInstancesForKey(owner);
			if (bankAccounts != null) {
				for (BankAccount bankAccount : new HashSet<BankAccount>(
						bankAccounts)) {
					if (bankAccount.getManagingBank() == managingBank)
						this.delete(bankAccount);
				}
			}
		}
	}

	@Override
	public synchronized List<BankAccount> findAllBankAccountsManagedByBank(
			Bank managingBank) {
		assureInitializedDataStructure(managingBank);

		final List<BankAccount> bankAccountManagedByBank = this.bankAccounts
				.get(managingBank);
		return new ArrayList<BankAccount>(bankAccountManagedByBank);
	}

	@Override
	public synchronized List<BankAccount> findAllBankAccountsOfAgent(
			BankCustomer owner) {
		final List<BankAccount> bankAccounts = this.getInstancesForKey(owner);
		if (bankAccounts != null) {
			return new ArrayList<BankAccount>(bankAccounts);
		}
		return new ArrayList<BankAccount>();
	}

	@Override
	public synchronized List<BankAccount> findAll(Bank managingBank,
			BankCustomer owner) {
		final List<BankAccount> bankAccounts = new ArrayList<BankAccount>();
		for (BankAccount bankAccount : this.findAllBankAccountsOfAgent(owner)) {
			if (bankAccount.getManagingBank() == managingBank) {
				bankAccounts.add(bankAccount);
			}
		}
		return bankAccounts;
	}

	@Override
	public synchronized List<BankAccount> findAll(Bank managingBank,
			BankCustomer owner, Currency currency) {
		final List<BankAccount> bankAccounts = new ArrayList<BankAccount>();
		for (BankAccount bankAccount : this.findAllBankAccountsOfAgent(owner)) {
			if (bankAccount.getManagingBank() == managingBank
					&& currency.equals(bankAccount.getCurrency())) {
				bankAccounts.add(bankAccount);
			}
		}
		return bankAccounts;
	}

	@Override
	public synchronized void save(BankAccount bankAccount) {
		assureInitializedDataStructure(bankAccount.getManagingBank());

		this.bankAccounts.get(bankAccount.getManagingBank()).add(bankAccount);
		super.save(bankAccount.getOwner(), bankAccount);
	}
}