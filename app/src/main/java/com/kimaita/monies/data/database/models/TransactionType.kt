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

fun getDefaultTransactionTypes(): List<TransactionType> {
    return listOf(
        TransactionType(
            name = "paybill",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) sent to (?<Name>.*) for account (?<Number>.*)on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\.? New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
            isInc = false,
            provider = "MPESA",
            displayName = "Paybill"
        ),
        TransactionType(
            name = "till",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) paid to (?<Name>.*) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\.New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Till"
        ),
        TransactionType(
            name = "send_money",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed. Ksh(?<Amount>[\d,]+\.\d{2}) sent to (?<Name>.*) (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Send Money"
        ),
        TransactionType(
            name = "pochi",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed. Ksh(?<Amount>[\d,]+\.\d{2}) sent to (?<Name>.*) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Pochi La Biashara"
        ),
        TransactionType(
            name = "received.person",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed.*You have received Ksh(?<Amount>[\d,]+\.\d{2}) from (?<Name>.*) (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}).)""",
            provider = "MPESA",
            isInc = true,
            displayName = "Received"
        ),
        TransactionType(
            name = "received.bank",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed.*You have received Ksh(?<Amount>[\d,]+\.\d{2}) from (?<Name>.*) on (?<Date>[\d\/]+) (at )?(?<Time>\d{1,}:\d{2} [AP]M).*New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}).)""",
            provider = "MPESA",
            isInc = true,
            displayName = "Received"
        ),
        TransactionType(
            name = "airtime.own",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.You bought Ksh(?<Amount>[\d,]+\.\d{2}) of (?<Name>airtime) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M)\.New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Airtime"
        ),
        TransactionType(
            name = "airtime.other",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.You bought Ksh(?<Amount>[\d,]+\.\d{2}) of (?<Name>airtime) for (?<Number>\d+) on (?<Date>[\d\/]+) at (?<Time>\d{1,}:\d{2} [AP]M)\.New *balance is Ksh(?<Balance>[\d,]+\.\d{2}). Transaction cost, Ksh(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Airtime"
        ),
        TransactionType(
            name = "cash_deposit",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\. On (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M) Give Ksh(?<Amount>[\d,\.]+) cash to\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = true,
            displayName = "Cash Deposit"
        ),
        TransactionType(
            name = "cash_withdrawal.atm",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. Ksh(?<Amount>[\d,\.]+) withdrawn from\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "ATM Withdrawal"
        ),
        TransactionType(
            name = "cash_withdrawal.agent",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed\.on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)Withdraw Ksh(?<Amount>[\d,\.]+) from\s+(?<Name>.*) New M-PESA balance is Ksh(?<Balance>[\d,]+\.\d{2})\. Transaction cost, Ksh(?<Cost>[\d]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Agent Withdrawal"
        ),
        TransactionType(
            name = "mshwari.deposit",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.Ksh(?<Amount>[\d,]+\.\d{2}) transferred to (?<Name>M-Shwari account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}) \.New M-Shwari saving account balance is Ksh(?<MshwariBal>[\d,]+\.\d{2})\. Transaction cost Ksh\.(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "M-Shwari Deposit"
        ),
        TransactionType(
            name = "mshwari.withdrawal",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\.Ksh(?<Amount>[\d,]+\.\d{2}) transferred from (?<Name>M-Shwari account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-Shwari balance is Ksh(?<MshwariBal>[\d,]+\.\d{2}) \.M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}) \.Transaction cost Ksh\.(?<Cost>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = true,
            displayName = "M-Shwari Withdrawal"
        ),
        TransactionType(
            name = "mshwari.lock_savings.deposit",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) [cC]onfirmed\. Ksh(?<Amount>[\d,]+\.\d{2}) transfered to (?<Name>Lock Savings Account) on (?<Date>[\d\/]+) at (?<Time>[\d:]+ [AP]M)\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2})\. Lock Savings Account balance is Ksh(?<LockSavingsBal>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Lock Savings Deposit"
        ),
        TransactionType(
            name = "mshwari.lock_savings.withdrawal",
            pattern = """Dear customer, Ksh (?<Amount>[\d,]+\.\d{2}) has been moved from your (?<Name>Lock Savings Account) to your M-Shwari account\. New M-Shwari balance is Ksh (?<MshwariBal>[\d,]+\.\d{2}). Transaction cost (?<Cost>[\d,]+\.\d{2})""",
            provider = "MPESA",
            isInc = false,
            displayName = "Lock Savings Withdrawal"
        ),
        TransactionType(
            name = "fuliza.loan",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10}) Confirmed.*(?<Name>Fuliza) M-PESA amount is Ksh (?<Amount>[\d,]+\.\d{2}).*(?:Interest|Access Fee|Fee) charged Ksh (?<Cost>[\d,]+\.\d{2}).*Total Fuliza M-PESA outstanding amount is Ksh.?(?<TotalLoan>[\d,]+\.\d{2}) due on (?<DueDate>[\d\/]+).)""",
            provider = "MPESA",
            isInc = true,
            displayName = "Fuliza Loan"
        ),
        TransactionType(
            name = "fuliza.partial_pay",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s*[cC]onfirmed.*Ksh (?<Amount>[\d,]+\.\d{2}).*from your M-PESA has been used to partially pay your outstanding (?<Name>Fuliza) M-PESA\. Your available Fuliza M-PESA limit is Ksh (?<AvailableFuliza>[\d,]+\.\d{2}).* M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}).)""",
            provider = "MPESA",
            isInc = false,
            displayName = "Fuliza Repayment"
        ),
        TransactionType(
            name = "fuliza.final_pay",
            pattern = """(?<Body>(?<Code>[A-Z0-9]{10})\s*[cC]onfirmed\. Ksh (?<Amount>[\d,]+\.\d{2}) from your M-PESA has been used to fully pay your outstanding (?<Name>Fuliza) M-PESA\. Available Fuliza M-PESA limit is Ksh (?<AvailableFuliza>[\d,]+\.\d{2})\. M-PESA balance is Ksh(?<MpesaBal>[\d,]+\.\d{2}))""",
            provider = "MPESA",
            isInc = false,
            displayName = "Fuliza Repayment"
        ),
    )
}

//TODO: Saf Dividend Payout
//Failed to parse message: SmsMessage(body=TCQ453QIC0 Confirmed.  You have received a dividend payment from SAFARICOM PLC INTERIM DIVIDEND 2025 of Ksh2,246.75 on 26/3/25  at 8:28 AM. Your new Account balance is Ksh2,255.34., id=14514, originatingAddress=MPESA, timestamp=1742966885632)
//Failed to parse message: SmsMessage(body=TCQ25D2GFG Confirmed.  You have received a dividend payment from SAFARICOM PLC INTERIM DIVIDEND 2025 of Ksh104.50 on 26/3/25  at 9:39 AM. Your new Account balance is Ksh100,359.84., id=14516, originatingAddress=MPESA, timestamp=1742971199934)
