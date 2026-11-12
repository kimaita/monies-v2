package com.kimaita.monies.data.database.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_types")
data class TransactionType(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isInc: Boolean,
    val pattern: String? = null,
    val provider: String = "MPESA",
    val description: String? = null,
    val isActive: Boolean = true,
    val displayName: String? = null,
) {
    @get:Ignore
    val finalDisplayName: String
        get() = displayName ?: name.replace('_', ' ').replace('.', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun mpesa(name: String, displayName: String, isInc: Boolean, pattern: String) =
    TransactionType(name = name, displayName = displayName, isInc = isInc, pattern = pattern)

/**
 * The built-in message formats, synced into the database on every open (see DatabaseInitializer).
 *
 * Patterns use named groups that [com.kimaita.monies.services.SmsParser] reads: Body, Code, Amount,
 * Cost, Name, Number, Date, Time, plus balance groups for BalanceExtractor (Balance, MpesaBal,
 * MshwariBal, LockSavingsBal, AvailableFuliza, TotalLoan).
 *
 * Order matters: the parser takes the first pattern that matches, so a more specific format must
 * come before a looser one (send_money before pochi, received.person before received.bank).
 * `name` is the stable key used to sync these rows, so don't rename an existing entry.
 */
fun getDefaultTransactionTypes(): List<TransactionType> = listOf(
    // Payments and transfers out
    mpesa(
        name = "paybill", displayName = "Paybill", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) sent to (?<Name>.*) for account (?<Number>.*)on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\.? New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "till", displayName = "Till", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) paid to (?<Name>.*) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\.New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "send_money", displayName = "Send Money", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed. Ksh(?<Amount>[\d,]+\.\d{2}) sent to (?<Name>.*) (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "pochi", displayName = "Pochi La Biashara", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed. Ksh(?<Amount>[\d,]+\.\d{2}) sent to (?<Name>.*) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),

    // Money in
    mpesa(
        name = "received.person", displayName = "Received", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed.*You have received Ksh(?<Amount>[\d,]+\.\d{2}) from (?<Name>.*) (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}).)""",
    ),
    mpesa(
        name = "received.bank", displayName = "Received", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed.*You have received Ksh(?<Amount>[\d,]+\.\d{2}) from (?<Name>.*) on (?<Date>[\d\/]+) (at )?(?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}).)""",
    ),
    // e.g. "...Confirmed.  You have received a dividend payment from SAFARICOM PLC INTERIM DIVIDEND 2025
    // of Ksh1,000.00 on 26/3/25  at 8:28 AM. Your new Account balance is Ksh2,000.00."
    // Name drops the "INTERIM DIVIDEND 2025" suffix so every payout groups under one payer.
    mpesa(
        name = "dividend", displayName = "Dividend", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\.\s+You have received a dividend payment from (?<Name>.+?)(?: (?:INTERIM|FINAL|SPECIAL) DIVIDEND \d{4})? of Ksh(?<Amount>[\d,]+\.\d{2}) on (?<Date>[\d\/]+)\s+at (?<Time>\d{1,2}:\d{2} [AP]M)\. Your new Account balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),

    // Airtime
    mpesa(
        name = "airtime.own", displayName = "Airtime", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.You bought Ksh(?<Amount>[\d,]+\.\d{2}) of (?<Name>airtime) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M)\.New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "airtime.other", displayName = "Airtime", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.You bought Ksh(?<Amount>[\d,]+\.\d{2}) of (?<Name>airtime) for (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M)\.New *balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),

    // Cash
    mpesa(
        name = "cash_deposit", displayName = "Cash Deposit", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. On (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M) Give Ksh(?<Amount>[\d,\.]+) cash to\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "cash_withdrawal.atm", displayName = "ATM Withdrawal", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. Ksh(?<Amount>[\d,\.]+) withdrawn from\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "cash_withdrawal.agent", displayName = "Agent Withdrawal", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\.on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)Withdraw Ksh(?<Amount>[\d,\.]+) from\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d]+\.\d{2}))""",
    ),

    // M-Shwari and Lock Savings
    mpesa(
        name = "mshwari.deposit", displayName = "M-Shwari Deposit", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.Ksh(?<Amount>[\d,]+\.\d{2}) transferred to (?<Name>M-Shwari account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}) \.New M-Shwari saving account balance is Ksh(?<MshwariBal>[\d,]+\.\d{2})\. Transaction cost Ksh\.(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.withdrawal", displayName = "M-Shwari Withdrawal", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.Ksh(?<Amount>[\d,]+\.\d{2}) transferred from (?<Name>M-Shwari account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-Shwari balance is Ksh(?<MshwariBal>[\d,]+\.\d{2}) \.M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}) \.Transaction cost Ksh\.(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.lock_savings.deposit", displayName = "Lock Savings Deposit", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) transfered to (?<Name>Lock Savings Account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2})\. Lock Savings Account balance is Ksh(?<LockSavingsBal>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.lock_savings.withdrawal", displayName = "Lock Savings Withdrawal", isInc = false,
        pattern = """Dear customer, Ksh (?<Amount>[\d,]+\.\d{2}) has been moved from your (?<Name>Lock Savings Account) to your M-Shwari account\. New M-Shwari balance is Ksh (?<MshwariBal>[\d,]+\.\d{2}). Transaction cost (?<Cost>[\d,]+\.\d{2})""",
    ),

    // Fuliza
    mpesa(
        name = "fuliza.loan", displayName = "Fuliza Loan", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed.*(?<Name>Fuliza) M-PESA amount is Ksh (?<Amount>[\d,]+\.\d{2}).*(?:Interest|Access Fee|Fee) charged Ksh (?<Cost>[\d,]+\.\d{2}).*Total Fuliza M-PESA outstanding amount is Ksh[. ]?(?<TotalLoan>[\d,]+\.\d{2}) due on (?<DueDate>[\d\/]+).)""",
    ),
    mpesa(
        name = "fuliza.partial_pay", displayName = "Fuliza Repayment", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s*[cC]onfirmed.*Ksh (?<Amount>[\d,]+\.\d{2}).*from your M-PESA has been used to partially pay your outstanding (?<Name>Fuliza) M-PESA\. Your available Fuliza M-PESA limit is Ksh (?<AvailableFuliza>[\d,]+\.\d{2}).* (?:Your\s+)?M-PESA balance is (?:Ksh)?(?<MpesaBal>[\d,]+\.\d{2}).)""",
    ),
    mpesa(
        name = "fuliza.final_pay", displayName = "Fuliza Repayment", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s*[cC]onfirmed\.*Ksh (?<Amount>[\d,]+\.\d{2}) from your M-PESA has been used to fully pay your outstanding (?<Name>Fuliza) M-PESA\. Available Fuliza M-PESA limit is Ksh (?<AvailableFuliza>[\d,]+\.\d{2})\. (?:Your\s+)?M-PESA balance is (?:Ksh)?(?<MpesaBal>[\d,]+\.\d{2}))""",
    ),
)
