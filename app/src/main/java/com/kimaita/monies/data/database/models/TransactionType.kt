package com.kimaita.monies.data.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.kimaita.monies.models.Provider

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
    @ColumnInfo(defaultValue = "0") val isTransfer: Boolean = false,
) {
    @get:Ignore
    val finalDisplayName: String
        get() = displayName ?: name.replace('_', ' ').replace('.', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/** M-Shwari and Fuliza movements are transfers between the user's own money, not income or spending. */
private fun mpesa(name: String, displayName: String, isInc: Boolean, pattern: String) =
    TransactionType(
        name = name, displayName = displayName, isInc = isInc, pattern = pattern,
        provider = Provider.MPESA.name,
        isTransfer = name.startsWith("mshwari.") || name.startsWith("fuliza."),
    )

private fun airtel(name: String, displayName: String, isInc: Boolean, pattern: String) =
    TransactionType(
        name = name, displayName = displayName, isInc = isInc, pattern = pattern,
        provider = Provider.AIRTEL.name,
    )

/** Airtel amounts may have no decimals ("Ksh 50", "Ksh 2,000") and balances one ("2004.0"). */
private const val AIRTEL_NUM = """[\d,]+(?:\.\d+)?"""
private const val AIRTEL_CODE = """[A-Z0-9]{10,11}"""
private const val AIRTEL_WHEN = """(?<Date>[\d\/]+)(?: at)? (?<Time>\d{1,2}:\d{2}(?: [AP]M)?)"""

/**
 * The built-in message formats, synced into the database on every open (see DatabaseInitializer).
 *
 * Patterns use named groups that [com.kimaita.monies.services.SmsParser] reads: Body, Code, Amount,
 * Cost, Name, Number, Date, Time, plus balance groups (Balance, then MpesaBal, becomes Transaction.balance) and the other balances (
 * MshwariBal, LockSavingsBal, AvailableFuliza, TotalLoan, LoanBal).
 * Names that need cleaning (trailing dot, repeated spaces) are cleaned by the parser, not here.
 * M-PESA hides some digits of the sender's number in received messages ("0712***880"), so
 * received.person's Number accepts asterisks; without that the message falls to received.bank.
 *
 * Order matters: the parser takes the first pattern that matches, so a more specific format must
 * come before a looser one (send_money before pochi, received.person before received.bank).
 * `name` is the stable key used to sync these rows, so don't rename an existing entry.
 */
fun getDefaultTransactionTypes(): List<TransactionType> = listOf(
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

    mpesa(
        name = "received.person", displayName = "Received", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed.*You have received Ksh(?<Amount>[\d,]+\.\d{2}) from (?<Name>.*) (?<Number>\d+(?:\*+\d+)?) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}).)""",
    ),
    mpesa(
        name = "received.bank", displayName = "Received", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed.*You have received Ksh(?<Amount>[\d,]+\.\d{2}) from (?<Name>.*) on (?<Date>[\d\/]+) (at )?(?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}).)""",
    ),
    mpesa(
        name = "dividend", displayName = "Dividend", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\.\s+You have received a dividend payment from (?<Name>.+?)(?: (?:INTERIM|FINAL|SPECIAL) DIVIDEND \d{4})? of Ksh(?<Amount>[\d,]+\.\d{2}) on (?<Date>[\d\/]+)\s+at (?<Time>\d{1,2}:\d{2} [AP]M)\. Your new Account balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),

    mpesa(
        name = "airtime.own", displayName = "Airtime", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.You bought Ksh(?<Amount>[\d,]+\.\d{2}) of (?<Name>airtime) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M)\.New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "airtime.other", displayName = "Airtime", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.You bought Ksh(?<Amount>[\d,]+\.\d{2}) of (?<Name>airtime) for (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M)\.New *balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
    ),

    mpesa(
        name = "cash_deposit", displayName = "Cash Deposit", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. On (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M) Give Ksh(?<Amount>[\d,\.]+) cash to\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "cash_withdrawal.atm", displayName = "ATM Withdrawal", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. Ksh(?<Amount>[\d,\.]+) withdrawn from\s+(?<Name>.*?)(?: ATM)?\.? New M-PESA\s+balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "cash_withdrawal.agent", displayName = "Agent Withdrawal", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\.on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)Withdraw Ksh(?<Amount>[\d,\.]+) from\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d]+\.\d{2}))""",
    ),

    mpesa(
        name = "mshwari.deposit", displayName = "M-Shwari Deposit", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.Ksh(?<Amount>[\d,]+\.\d{2}) transferred to (?<Name>M-Shwari account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}) \.New M-Shwari saving account balance is Ksh(?<MshwariBal>[\d,]+\.\d{2})\. Transaction cost Ksh\.(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.withdrawal", displayName = "M-Shwari Withdrawal", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.Ksh(?<Amount>[\d,]+\.\d{2}) transferred from (?<Name>M-Shwari account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-Shwari balance is Ksh(?<MshwariBal>[\d,]+\.\d{2}) \.M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}) \.Transaction cost Ksh\.(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.loan", displayName = "M-Shwari Loan", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. Your M-Shwari loan has been approved on (?<Date>[\d\/]+) (?<Time>\d{1,2}:\d{2} [AP]M) and Ksh(?<Amount>[\d,]+\.\d{2}) less excise duty has been deposited to your M-PESA account\. New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.loan_repayment", displayName = "M-Shwari Loan Repayment", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. Loan of Ksh(?<Amount>[\d,]+\.\d{2}) repaid from (?:your M-PESA account|M-PESA) on (?<Date>[\d\/]+) at (?<Time>\d{1,2}:\d{2} [AP]M)\. (?:M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2})\..*?New M-Shwari balance is Ksh(?<MshwariBal>[\d,]+\.\d{2})|Loan balance is Ksh(?<LoanBal>[\d,]+\.\d{2}))\.\s+Transaction cost Kshs?\.? ?(?<Cost>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.lock_savings.deposit", displayName = "Lock Savings Deposit", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) transfered to (?<Name>Lock Savings Account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2})\. Lock Savings Account balance is Ksh(?<LockSavingsBal>[\d,]+\.\d{2}))""",
    ),
    mpesa(
        name = "mshwari.lock_savings.withdrawal", displayName = "Lock Savings Withdrawal", isInc = false,
        pattern = """Dear customer, Ksh (?<Amount>[\d,]+\.\d{2}) has been moved from your (?<Name>Lock Savings Account) to your M-Shwari account\. New M-Shwari balance is Ksh (?<MshwariBal>[\d,]+\.\d{2}). Transaction cost (?<Cost>[\d,]+\.\d{2})""",
    ),

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
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s*[cC]onfirmed\.\s*Ksh (?<Amount>[\d,]+\.\d{2}) from your M-PESA has been used to fully pay your outstanding (?<Name>Fuliza) M-PESA\. Available Fuliza M-PESA limit is Ksh (?<AvailableFuliza>[\d,]+\.\d{2})\. (?:Your\s+)?M-PESA balance is (?:Ksh)?(?<MpesaBal>[\d,]+\.\d{2}))""",
    ),

    mpesa(
        name = "reversal", displayName = "Reversal", isInc = true,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s+confirmed\. (?=(?:Your original transaction [A-Z0-9]{10} in favour of )?(?<Name>Reversal|.+?) (?:of transaction|has been reversed))(?:Reversal of transaction|Your original transaction) [A-Z0-9]{10}.*?(?:successfully reversed|reversed successfully|completed successfully)\s+on (?<Date>[\d\/]+)\s+at (?<Time>\d{1,2}:\d{2} [AP]M)\s+and Ksh(?<Amount>[\d,]+\.\d{2}) (?:is|has been) credited to your M-PESA account\.(?: (?:Your )?new M-PESA account balance is Ksh(?<Balance>[\d,]+\.\d{2}))?)""",
    ),
    mpesa(
        name = "reversal.debit", displayName = "Reversal (debit)", isInc = false,
        pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s+confirmed\. (?<Name>Reversal) of transaction [A-Z0-9]{10}.*?(?:successfully reversed|reversed successfully|completed successfully)\s+on (?<Date>[\d\/]+)\s+at (?<Time>\d{1,2}:\d{2} [AP]M)\s+and Ksh(?<Amount>[\d,]+\.\d{2}) (?:is|has been) debited from your M-PESA account\.(?: (?:Your )?new M-PESA account balance is Ksh(?<Balance>[\d,]+\.\d{2}))?)""",
    ),

    airtel(
        name = "airtel.send_money", displayName = "Airtel Send Money", isInc = false,
        pattern = """(?<Body>(?<Code>$AIRTEL_CODE)\. Ksh (?<Amount>$AIRTEL_NUM) sent to (?<Name>.+?) (?<Number>\d+) on $AIRTEL_WHEN\. Fee: Ksh (?<Cost>$AIRTEL_NUM)\. Bal: Ksh (?<Balance>$AIRTEL_NUM)\.(?: Receiving TID: [A-Z0-9]+\.?)?)""",
    ),
    airtel(
        name = "airtel.pay", displayName = "Airtel Pay", isInc = false,
        pattern = """(?<Body>(?<Code>$AIRTEL_CODE)(?: Confirmed)?\. Ksh (?<Amount>$AIRTEL_NUM) (?:successfully )?paid to (?!Lipa Na Mpesa via)(?<Name>.+?)(?: (?:for )?account (?<Number>\S+))? on $AIRTEL_WHEN\. ?(?:Transaction cost|Cost|Fee:?) Ksh (?<Cost>$AIRTEL_NUM)\. (?:New Airtel Money )?Bal:\s*Ksh (?<Balance>$AIRTEL_NUM)\.?(?: (?:Receiving (?:TXN ID|TID):?|MPESA ID:) ?[A-Z0-9]+\.?)?)""",
    ),
    airtel(
        name = "airtel.pay.receipt", displayName = "Airtel Pay", isInc = false,
        pattern = """(?<Body>(?<Code>$AIRTEL_CODE)\. Ksh (?<Amount>$AIRTEL_NUM) paid to Lipa Na Mpesa via Airtel Money on $AIRTEL_WHEN\. Fee: Ksh (?<Cost>$AIRTEL_NUM)\. Bal: Ksh (?<Balance>$AIRTEL_NUM)\.?(?: Receiving TID: [A-Z0-9]+)?)""",
    ),
    airtel(
        name = "airtel.received", displayName = "Airtel Received", isInc = true,
        pattern = """(?<Body>(?=(?:.*?(?:TID|Txn ID): ?)?(?<Code>$AIRTEL_CODE))(?:TID:\w+\. |\w+\. )?(?:(?:You have received|Received) Ksh |(?=Ksh $AIRTEL_NUM received )Ksh )(?<Amount>$AIRTEL_NUM)(?: received)? from (?:MPESA(?: -)? )?(?<Name>.+?)(?: (?<Number>(?:254|0)\d{9}))?(?: on $AIRTEL_WHEN)?\.(?: (?:TID|Txn ID): [A-Z0-9]+\.)?(?: Fee: Ksh (?<Cost>$AIRTEL_NUM)\.)? ?Bal:\s*Ksh\s*(?<Balance>$AIRTEL_NUM))""",
    ),
    airtel(
        name = "airtel.airtime", displayName = "Airtel Airtime", isInc = false,
        pattern = """(?<Body>(?<Code>\d{10,11}) successful\. (?<Name>Airtime) top up of Ksh (?<Amount>$AIRTEL_NUM) to (?<Number>\d+) on $AIRTEL_WHEN\.)""",
    ),
    airtel(
        name = "airtel.airtime.line", displayName = "Airtel Airtime", isInc = false,
        pattern = """(?<Body>(?<Code>$AIRTEL_CODE) successful\. (?<Name>Airtime) top up (?:for|of your) line (?<Number>\d+) of Ksh (?<Amount>$AIRTEL_NUM) is successful\.(?: \d+% bonus airtime received\.)? Bal: Ksh (?<Balance>$AIRTEL_NUM)\.?)""",
    ),
    airtel(
        name = "airtel.bundle", displayName = "Airtel Bundle", isInc = false,
        pattern = """(?<Body>(?<Code>$AIRTEL_CODE) Confirmed\. (?:You have successfully purchased a bundle|Bundle purchase successful) of Ksh (?<Amount>$AIRTEL_NUM) (?:for your line )?via (?<Name>Airtel Networks Kenya Ltd) on $AIRTEL_WHEN\.(?: Fee: Ksh (?<Cost>$AIRTEL_NUM)\.)? Bal: Ksh (?<Balance>$AIRTEL_NUM)\.?)""",
    ),
    airtel(
        name = "airtel.withdrawal", displayName = "Airtel Withdrawal", isInc = false,
        pattern = """(?<Body>(?<Code>$AIRTEL_CODE) Confirmed\. You have withdrawn Ksh (?<Amount>$AIRTEL_NUM) from (?<Name>.+?) on $AIRTEL_WHEN\. Fee: Ksh (?<Cost>$AIRTEL_NUM)\.? Bal:?\s*Ksh (?<Balance>$AIRTEL_NUM)\.?)""",
    ),
)
