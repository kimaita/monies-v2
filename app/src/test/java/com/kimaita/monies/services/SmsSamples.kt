package com.kimaita.monies.services

import java.time.LocalDateTime

/**
 * Anonymised copies of real mobile-money messages. Names and phone numbers are fake; spacing, line
 * breaks and trailing marketing text are as received. [dateTime] is null when the message carries
 * no date, so the parser falls back to the SMS timestamp.
 */
data class Sample(
    val body: String,
    val sender: String,
    val type: String,
    val amount: Double,
    val cost: Double,
    val balance: Double?,
    val name: String?,
    val number: String?,
    val dateTime: LocalDateTime?,
)

private fun dt(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi)

private const val MARKETING = "Amount you can transact within the day is 468,190.00. Download new M-PESA app on http://bit.ly/mpesappsm & get 500MB FREE data."

val mpesaSamples: List<Sample> = listOf(
    // Paybill
    Sample(
        "HAZ3P4FN21 confirmed. Ksh100.00 sent to AIRTEL MONEY  for account 254700000001 on 15/11/24 at 7:12 PM New M-PESA balance is Ksh399.72. Transaction cost, Ksh0.00.",
        "MPESA", "paybill", 100.0, 0.0, 399.72, "AIRTEL MONEY", "254700000001", dt(2024, 11, 15, 19, 12),
    ),
    Sample(
        "EA03G2FKL3 confirmed. Ksh100.00 sent to AIRTEL MONEY  for account 254700000001 on 2/11/24 at 7:47 PM New M-PESA balance is Ksh9,323.64. Transaction cost, Ksh0.00.",
        "MPESA", "paybill", 100.0, 0.0, 9323.64, "AIRTEL MONEY", "254700000001", dt(2024, 11, 2, 19, 47),
    ),
    Sample(
        "AM6224CLKV Confirmed. Ksh50.00 sent to ACME UTILITY for account 37100000000 on 8/11/24 at 9:41 AM New M-PESA balance is Ksh195.14. Transaction cost, Ksh0.00.$MARKETING",
        "MPESA", "paybill", 50.0, 0.0, 195.14, "ACME UTILITY", "37100000000", dt(2024, 11, 8, 9, 41),
    ),
    Sample(
        "MK75FRRYFB Confirmed. Ksh450.00 sent to ACME GUILD for account Doe/AcmeShirt on 3/11/24 at 5:35 PM New M-PESA balance is Ksh505.69. Transaction cost, Ksh5.00.$MARKETING",
        "MPESA", "paybill", 450.0, 5.0, 505.69, "ACME GUILD", "Doe/AcmeShirt", dt(2024, 11, 3, 17, 35),
    ),
    Sample(
        "PM69AQBY2V Confirmed. Ksh10.00 sent to SAFARICOM DATA BUNDLES for account SAFARICOM DATA BUNDLES on 2/11/24 at 7:17 PM. New M-PESA balance is Ksh4,137.58. Transaction cost, Ksh0.00.",
        "MPESA", "paybill", 10.0, 0.0, 4137.58, "SAFARICOM DATA BUNDLES", "SAFARICOM DATA BUNDLES", dt(2024, 11, 2, 19, 17),
    ),
    // Till (trailing dot on the name)
    Sample(
        "MS47D9ZMKB Confirmed. Ksh45.00 paid to PAUL KAMAU. on 7/11/24 at 6:34 AM.New M-PESA balance is Ksh713.58. Transaction cost, Ksh0.00. Amount you can transact within the day is 625,481.00. Download new M-PESA app on http://bit.ly/mpesappsm & get 500MB FREE data.",
        "MPESA", "till", 45.0, 0.0, 713.58, "PAUL KAMAU", null, dt(2024, 11, 7, 6, 34),
    ),
    // Send money, Pochi
    Sample(
        "TQZA6PZV7W Confirmed. Ksh99.00 sent to JOHN  DOE 0700000001 on 5/11/24 at 8:54 PM. New M-PESA balance is Ksh613.00. Transaction cost, Ksh0.00. Amount you can transact within the day is 918,178.77. Thank you for being our valued customer, we appreciate you.",
        "MPESA", "send_money", 99.0, 0.0, 613.00, "JOHN DOE", "0700000001", dt(2024, 11, 5, 20, 54),
    ),
    Sample(
        "VT60HZVMJQ Confirmed. Ksh60.00 sent to Mary  Doe on 5/11/24 at 8:05 PM. New M-PESA balance is Ksh720.07. Transaction cost, Ksh0.00. Amount you can transact within the day is 632,920.00. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke",
        "MPESA", "pochi", 60.0, 0.0, 720.07, "Mary Doe", null, dt(2024, 11, 5, 20, 5),
    ),
    // Received
    Sample(
        "WP12PM2BPK Confirmed.You have received Ksh200.00 from JANE  DOE 0700000002 on 3/11/24 at 9:12 PM  New M-PESA balance is Ksh379.28. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke",
        "MPESA", "received.person", 200.0, 0.0, 379.28, "JANE DOE", "0700000002", dt(2024, 11, 3, 21, 12),
    ),
    Sample(
        "TCQ000CCC3 Confirmed.You have received Ksh500.00 from JANE DOE 0700000003 on 2/4/25 at 10:15 AM  New M-PESA balance is Ksh3,500.00.",
        "MPESA", "received.person", 500.0, 0.0, 3500.0, "JANE DOE", "0700000003", dt(2025, 4, 2, 10, 15),
    ),
    Sample(
        "QR574KRN21 Confirmed. You have received Ksh60.00 from ACME BANK LIMITED- APP on 1/11/24 at 9:34 AM. New M-PESA balance is Ksh60.00. Buy goods with M-PESA.",
        "MPESA", "received.bank", 60.0, 0.0, 60.0, "ACME BANK LIMITED- APP", null, dt(2024, 11, 1, 9, 34),
    ),
    // Dividends
    Sample(
        "TCQ000AAA1 Confirmed.  You have received a dividend payment from SAFARICOM PLC INTERIM DIVIDEND 2025 of Ksh1,234.56 on 26/3/25  at 8:28 AM. Your new Account balance is Ksh2,000.00.",
        "MPESA", "dividend", 1234.56, 0.0, 2000.0, "SAFARICOM PLC", null, dt(2025, 3, 26, 8, 28),
    ),
    Sample(
        "TCQ000BBB2 Confirmed.  You have received a dividend payment from SAFARICOM PLC FINAL DIVIDEND 2024 of Ksh104.50 on 1/9/24  at 9:39 PM. Your new Account balance is Ksh100,359.84.",
        "MPESA", "dividend", 104.5, 0.0, 100359.84, "SAFARICOM PLC", null, dt(2024, 9, 1, 21, 39),
    ),
    // Pay with Fuliza: the purchase, then the Fuliza notice with the same code
    Sample(
        "NT275FCR4S Confirmed. Ksh60.00 paid to PAUL KAMAU. on 1/11/24 at 9:40 AM.New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00. Amount you can transact within the day is 985,150.00. Download new M-PESA app on http://bit.ly/mpesappsm & get 500MB FREE data.",
        "MPESA", "till", 60.0, 0.0, 0.0, "PAUL KAMAU", null, dt(2024, 11, 1, 9, 40),
    ),
    Sample(
        "NT275FCR4S Confirmed. Fuliza M-PESA amount is Ksh 60.00. Interest charged Ksh 0.60. Total Fuliza M-PESA outstanding amount is Ksh 145.84 due on 28/11/24. To check daily charges, Dial *234*0#OK Select Query Charges",
        "MPESA", "fuliza.loan", 60.0, 0.6, null, "Fuliza", null, null,
    ),
    Sample(
        "RJ452UARTJ\n Confirmed. Ksh 60.00 from your M-PESA has been used to partially pay your outstanding Fuliza M-PESA. Your available Fuliza M-PESA limit is Ksh 647.48. M-PESA balance is Ksh0.00.",
        "MPESA", "fuliza.partial_pay", 60.0, 0.0, 0.0, "Fuliza", null, null,
    ),
    Sample(
        "WX554W56MF\n Confirmed. Ksh 145.84 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 700.00. M-PESA balance is Ksh8,558.28.",
        "MPESA", "fuliza.final_pay", 145.84, 0.0, 8558.28, "Fuliza", null, null,
    ),
    Sample(
        "FGM7416FV9\n Confirmed. Ksh 964.98 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 1800.00. Your M-PESA balance is 50329.79.",
        "MPESA", "fuliza.final_pay", 964.98, 0.0, 50329.79, "Fuliza", null, null,
    ),
    // Airtime
    Sample(
        "WFH20RH2BE confirmed.You bought Ksh5.00 of airtime on 24/10/24 at 7:51 AM.New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00. Amount you can transact within the day is 252,738.00. Buy airtime from 1 bob via M-PESA today & keep that conversation going!",
        "MPESA", "airtime.own", 5.0, 0.0, 0.0, "airtime", null, dt(2024, 10, 24, 7, 51),
    ),
    // Cash
    Sample(
        "BPQ5NX91U3 Confirmed. On 11/10/24 at 11:45 AM Give Ksh2,400.00 cash to Acme Widgets Ltd Plaza Springfield Townsville Qux New M-PESA balance is Ksh4,505.64. You can now access M-PESA via *334#",
        "MPESA", "cash_deposit", 2400.0, 0.0, 4505.64, "Acme Widgets Ltd Plaza Springfield Townsville Qux", null, dt(2024, 10, 11, 11, 45),
    ),
    Sample(
        "PDY8MBT9K1 Confirmed on 27/8/22 at 10:57 AM. Ksh58,800.00 withdrawn from ACME BANK ATM ATM. New M-PESA  balance is Ksh6,945.26.",
        "MPESA", "cash_withdrawal.atm", 58800.0, 0.0, 6945.26, "ACME BANK ATM", null, dt(2022, 8, 27, 10, 57),
    ),
    Sample(
        "PUW8MQE29N Confirmed.on 14/3/23 at 7:27 PMWithdraw Ksh800.00 from 832915 - Acme Holdings ltdTrading Tools And Supplies New M-PESA balance is Ksh161.59. Transaction cost, Ksh28.00. Amount you can transact within the day is 925,960.00. Withdraw your Fuliza limit at any M-Pesa agent Click https://bit.ly/7xyz31Q or dial *334#",
        "MPESA", "cash_withdrawal.agent", 800.0, 28.0, 161.59, "832915 - Acme Holdings ltdTrading Tools And Supplies", null, dt(2023, 3, 14, 19, 27),
    ),
    // M-Shwari and Lock Savings
    Sample(
        "PP37KRYNN2 Confirmed.Ksh50.00 transferred from M-Shwari account on 7/9/24 at 2:09 PM. M-Shwari balance is Ksh5.47 .M-PESA balance is Ksh56.05 .Transaction cost Ksh.0.00",
        "MPESA", "mshwari.withdrawal", 50.0, 0.0, 56.05, "M-Shwari account", null, dt(2024, 9, 7, 14, 9),
    ),
    Sample(
        "KR42R019Q3 Confirmed.Ksh70.00 transferred from M-Shwari account on 2/8/24 at 4:55 PM. M-Shwari balance is Ksh50.26 .M-PESA balance is Ksh178.84 .Transaction cost Ksh.0.00",
        "MPESA", "mshwari.withdrawal", 70.0, 0.0, 178.84, "M-Shwari account", null, dt(2024, 8, 2, 16, 55),
    ),
    Sample(
        "HRB3RJE44R Confirmed.Ksh2,000.00 transferred to M-Shwari account on 25/7/24 at 2:47 PM. M-PESA balance is Ksh889.13 .New M-Shwari saving account balance is Ksh54,859.48. Transaction cost Ksh.0.00",
        "MPESA", "mshwari.deposit", 2000.0, 0.0, 889.13, "M-Shwari account", null, dt(2024, 7, 25, 14, 47),
    ),
    Sample(
        "AQM5X9E0A4 Confirmed. Ksh10.00 transfered to Lock Savings Account on 10/7/24 at 11:01 PM. M-PESA balance is Ksh97.18. Lock Savings Account balance is Ksh10.00.",
        "MPESA", "mshwari.lock_savings.deposit", 10.0, 0.0, 97.18, "Lock Savings Account", null, dt(2024, 7, 10, 23, 1),
    ),
    Sample(
        "Dear customer, Ksh 40.66 has been moved from your Lock Savings Account to your M-Shwari account. New M-Shwari balance is Ksh 86.76. Transaction cost 0.00",
        "M-Shwari", "mshwari.lock_savings.withdrawal", 40.66, 0.0, null, "Lock Savings Account", null, null,
    ),
    // M-Shwari loan and repayment
    Sample(
        "XZ3471KAYP Confirmed. Your M-Shwari loan has been approved on 7/3/26 10:24 AM and Ksh574.22 less excise duty has been deposited to your M-PESA account. New M-PESA balance is Ksh574.22. Did you know you can top up your M-Shwari loan once within the first 20 days?",
        "MPESA", "mshwari.loan", 574.22, 0.0, 574.22, null, null, dt(2026, 3, 7, 10, 24),
    ),
    Sample(
        "RYT758E6U8 Confirmed. Your M-Shwari loan has been approved on 20/1/26 4:02 PM and Ksh113.40 less excise duty has been deposited to your M-PESA account. New M-PESA balance is Ksh113.40.",
        "MPESA", "mshwari.loan", 113.4, 0.0, 113.4, null, null, dt(2026, 1, 20, 16, 2),
    ),
    Sample(
        "CCX0561NYR Confirmed. Loan of Ksh700.00 repaid from your M-PESA account on 10/3/26 at 7:19 PM. M-PESA balance is Ksh11,139.12.Your loan is repaid in full, and the overpayment has been moved to your Savings Account. New M-Shwari balance is Ksh57.22. Transaction cost Kshs 0.00",
        "MPESA", "mshwari.loan_repayment", 700.0, 0.0, 11139.12, null, null, dt(2026, 3, 10, 19, 19),
    ),
    Sample(
        "VFP21930T1 Confirmed. Loan of Ksh714.00 repaid from your M-PESA account on 29/1/26 at 9:34 AM. M-PESA balance is Ksh1,991.69.Your loan is repaid in full, and the overpayment has been moved to your Savings Account. New M-Shwari balance is Ksh0.94. Transaction cost Kshs 0.00",
        "MPESA", "mshwari.loan_repayment", 714.0, 0.0, 1991.69, null, null, dt(2026, 1, 29, 9, 34),
    ),
    // Reversals
    Sample(
        "EAP1L9RRM5 confirmed. Reversal of transaction BEY4E9WXSK has been successfully reversed  on 28/1/23  at 11:41 AM and Ksh10.00 is credited to your M-PESA account. New M-PESA account balance is Ksh989.34.",
        "MPESA", "reversal", 10.0, 0.0, 989.34, "Reversal", null, dt(2023, 1, 28, 11, 41),
    ),
    Sample(
        "CT34KSEH5Q confirmed. Reversal of transaction ZN22YAWL1U has been successfully reversed  on 7/6/23  at 10:14 PM and Ksh150.00 is credited to your M-PESA account. New M-PESA account balance is Ksh111.00.",
        "MPESA", "reversal", 150.0, 0.0, 111.0, "Reversal", null, dt(2023, 6, 7, 22, 14),
    ),
    Sample(
        "SV736QRLD8 confirmed. Your original transaction AT60263ZDC in favour of JANE    ANN    DOE has been reversed successfully on 3/12/25 at 9:02 PM  and Ksh137.00 has been credited to your M-PESA account. Your new M-PESA account balance is Ksh137.00.",
        "MPESA", "reversal", 137.0, 0.0, 137.0, "JANE ANN DOE", null, dt(2025, 12, 3, 21, 2),
    ),
)
