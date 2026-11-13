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
        "VJ58A7HW0F Confirmed. Ksh99.00 sent to ALEX  DOE 0700000001 on 5/11/24 at 8:54 PM. New M-PESA balance is Ksh554.21. Transaction cost, Ksh0.00. Amount you can transact within the day is 347,201.00. Thank you for being our valued customer, we appreciate you.",
        "MPESA", "send_money", 99.0, 0.0, 554.21, "ALEX DOE", "0700000001", dt(2024, 11, 5, 20, 54),
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
    // Short form: "repaid from M-PESA", only the loan balance is stated (no M-PESA or M-Shwari balance)
    Sample(
        "TCQ000EEE5 Confirmed. Loan of Ksh350.00 repaid from M-PESA on 12/4/26 at 6:45 PM. Loan balance is Ksh137.50.  Transaction cost Kshs 0.00",
        "MPESA", "mshwari.loan_repayment", 350.0, 0.0, null, null, null, dt(2026, 4, 12, 18, 45),
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
    // Credit without a balance sentence ("completed successfully", "M-Pesa Account")
    Sample(
        "TCQ000FFF6 confirmed. Reversal of transaction TCQ000FFF5 has been completed successfully on 15/5/26 at 3:20 PM and Ksh63.50 has been credited to your M-Pesa Account.",
        "MPESA", "reversal", 63.5, 0.0, null, "Reversal", null, dt(2026, 5, 15, 15, 20),
    ),
    // Debit: money leaves the account (double space after the code, as received)
    Sample(
        "TCQ000GGG7  confirmed. Reversal of transaction TCQ000GGG6 has been successfully reversed  on 16/5/26  at 9:05 PM and Ksh36.40 is debited from your M-PESA account. New M-PESA account balance is Ksh260.00.",
        "MPESA", "reversal.debit", 36.4, 0.0, 260.0, "Reversal", null, dt(2026, 5, 16, 21, 5),
    ),
)

val airtelSamples: List<Sample> = listOf(
    // Send money (to a Safaricom number, to an Airtel number) and Pochi La Biashara
    Sample(
        "X7GEY96MH4L. Ksh 50 sent to JANE DOE 700000001 on 10/12/25 at 12:13 PM. Fee: Ksh 0. Bal: Ksh 666.0. Receiving TID: EZM4W6MSBU.",
        "airtelmoney", "airtel.send_money", 50.0, 0.0, 666.0, "JANE DOE", "700000001", dt(2025, 12, 10, 12, 13),
    ),
    Sample(
        "93Z2NCDBBFG. Ksh 300 sent to ALEX DOE 700000002 on 04/12/25 at 03:00 PM. Fee: Ksh 6. Bal: Ksh 39.0. Receiving TID: GB7750674V.",
        "airtelmoney", "airtel.send_money", 300.0, 6.0, 39.0, "ALEX DOE", "700000002", dt(2025, 12, 4, 15, 0),
    ),
    Sample(
        "65QQ1GE2CMN. Ksh 20 sent to Jane Doe 0700000001 on 17/11/25 04:18 PM. Fee: Ksh 0. Bal: Ksh 0.0.",
        "airtelmoney", "airtel.send_money", 20.0, 0.0, 0.0, "Jane Doe", "0700000001", dt(2025, 11, 17, 16, 18),
    ),
    Sample(
        "R2TJDJ24XHB. Ksh 40 sent to MARY WANJIKU 700000003 on 13/12/25 at 10:54 AM. Fee: Ksh 0. Bal: Ksh 805.0. Receiving TID: MLB0X7R2DX.",
        "airtelmoney", "airtel.send_money", 40.0, 0.0, 805.0, "MARY WANJIKU", "700000003", dt(2025, 12, 13, 10, 54),
    ),
    Sample(
        "54QXHZFBGY0. Ksh 70 sent to SAMUEL KAMAU 700000004 on 12/12/25 at 06:45 PM. Fee: Ksh 0. Bal: Ksh 419.0. Receiving TID: NYMXK2Q5KC.",
        "airtelmoney", "airtel.send_money", 70.0, 0.0, 419.0, "SAMUEL KAMAU", "700000004", dt(2025, 12, 12, 18, 45),
    ),
    // Till: the receipt notice, then the notice that names the merchant (same code), then a payment to Airtel
    Sample(
        "Z7FH2XGBWV6. Ksh 100 paid to Lipa Na Mpesa via Airtel Money on 11/12/25 at 05:22 PM. Fee: Ksh 0. Bal: Ksh 507.0. Receiving TID: BQXP20BTS9",
        "airtelmoney", "airtel.pay.receipt", 100.0, 0.0, 507.0, null, null, dt(2025, 12, 11, 17, 22),
    ),
    Sample(
        "Z7FH2XGBWV6 Confirmed. Ksh 100 paid to ACME COLLEGE REFECTORY SECTION for account 5000001 on 11/12/2025 at 17:22. Cost Ksh 0. New Airtel Money Bal:Ksh 507.0. Receiving TXN ID BQXP20BTS9.",
        "airtelmoney", "airtel.pay", 100.0, 0.0, 507.0, "ACME COLLEGE REFECTORY SECTION", "5000001", dt(2025, 12, 11, 17, 22),
    ),
    Sample(
        "15113717030 Confirmed. Ksh 80.00 successfully paid to Airtel Networks on 30/08/24 at 05:54 PM. Transaction cost Ksh 0.00. Bal: Ksh 22.00.",
        "airtelmoney", "airtel.pay", 80.0, 0.0, 22.0, "Airtel Networks", null, dt(2024, 8, 30, 17, 54),
    ),
    // Till and paybill wordings: "Fee:" instead of "Cost", "Receiving TID" without a colon, no space before "Fee:"
    Sample(
        "AB1CD2EF3GH Confirmed. Ksh 250 successfully paid to Acme Traders and Sons Ltd on 05/01/26 at 02:15 PM. Fee: Ksh 0. Bal: Ksh 1500.0.",
        "airtelmoney", "airtel.pay", 250.0, 0.0, 1500.0, "Acme Traders and Sons Ltd", null, dt(2026, 1, 5, 14, 15),
    ),
    Sample(
        "CD2EF3GH4IJ Confirmed. Ksh 137 paid to     ACME WATER SERVICES for account 4000002 on 06/01/2026 at 09:30.Fee: Ksh 0. Bal:Ksh 913.0. Receiving TID TLX9Y8Z7WV.",
        "airtelmoney", "airtel.pay", 137.0, 0.0, 913.0, "ACME WATER SERVICES", "4000002", dt(2026, 1, 6, 9, 30),
    ),
    // No "Confirmed.", "account" without "for", no "at" before the time, ends with the M-PESA id
    Sample(
        "EF3GH4IJ5KL. Ksh 167 paid to ACME GROCERIES account 5000003 on 07/01/2026 18:05. Fee Ksh 2. Bal:Ksh 640.0. MPESA ID:TLY8Z7X6WV",
        "airtelmoney", "airtel.pay", 167.0, 2.0, 640.0, "ACME GROCERIES", "5000003", dt(2026, 1, 7, 18, 5),
    ),
    // Received
    Sample(
        "You have received Ksh 100.00 from MPESA - ALEX DOE 254700000002. TID: 17600467400. Fee: Ksh 0.00. Bal: Ksh 428.00.",
        "airtelmoney", "airtel.received", 100.0, 0.0, 428.0, "ALEX DOE", "254700000002", null,
    ),
    Sample(
        "You have received Ksh 20.00 from MPESA - ALEX DOE 254700000002. Txn ID: 16383151182. fee: Ksh 0.00. Bal: Ksh 20.00. Pay at any M-PESA Till using Airtel Money! Dial *334# or use My Airtel App>Buy goods",
        "airtelmoney", "airtel.received", 20.0, 0.0, 20.0, "ALEX DOE", "254700000002", null,
    ),
    Sample(
        "You have received Ksh 20.00 from MPESA - ALEX DOE 254700000002. Txn ID: 19139078576. fee: Ksh 0.00. Bal: Ksh 24.00. Pay at any M-PESA Till using Airtel Money! Dial *334# or use My Airtel App>Buy goods",
        "airtelmoney", "airtel.received", 20.0, 0.0, 24.0, "ALEX DOE", "254700000002", null,
    ),
    Sample(
        "TID:U6SWEZAKYQ3. Received Ksh 50 from ALEX DOE 254700000002 on 02/12/25 03:56 PM. Bal:Ksh 50.0 Sender TID:TD247CJNL2.",
        "airtelmoney", "airtel.received", 50.0, 0.0, 50.0, "ALEX DOE", "254700000002", dt(2025, 12, 2, 15, 56),
    ),
    Sample(
        "You have received Ksh 100.00 from MPESA ALEX DOE 254700000002. TID: 12817316934. Fee: Ksh 0.00. Bal: Ksh 137.00.",
        "airtelmoney", "airtel.received", 100.0, 0.0, 137.0, "ALEX DOE", "254700000002", null,
    ),
    Sample(
        "B7Y30YG4MDC. You have received Ksh 2,000 from ACME BANK B2C on 05/12/25 08:50 PM. Bal: Ksh 7568.0.",
        "airtelmoney", "airtel.received", 2000.0, 0.0, 7568.0, "ACME BANK B2C", null, dt(2025, 12, 5, 20, 50),
    ),
    Sample(
        "GH4IJ5KL6MN. Ksh 1,500 received from JANE DOE 0700000004 on 08/01/26 at 04:40 PM.Bal: Ksh 2300.0.",
        "airtelmoney", "airtel.received", 1500.0, 0.0, 2300.0, "JANE DOE", "0700000004", dt(2026, 1, 8, 16, 40),
    ),
    // Airtime
    Sample(
        "10429072006 successful. Airtime top up of Ksh 10.00 to 700000005 on 10/05/23 at 02:20 PM. 25% bonus airtime received. Dial *131*1# to check bonus",
        "airtelmoney", "airtel.airtime", 10.0, 0.0, null, "Airtime", "700000005", dt(2023, 5, 10, 14, 20),
    ),
    Sample(
        "14168251422 successful. Airtime top up of Ksh 20.00 to 700000005 on 09/05/23 at 08:51 PM. 25% bonus airtime received. Dial *131*1# to check bonus",
        "airtelmoney", "airtel.airtime", 20.0, 0.0, null, "Airtime", "700000005", dt(2023, 5, 9, 20, 51),
    ),
    // Short form: the line is named, there is no date, but the balance is given
    Sample(
        "10000000001 Successful. Airtime top up for line 700000006 of Ksh 37 is successful. 25% bonus airtime received. Bal: Ksh 52.5. Dial *131*1# to check bonus.",
        "airtelmoney", "airtel.airtime.line", 37.0, 0.0, 52.5, "Airtime", "700000006", null,
    ),
    Sample(
        "10000000002 Successful. Airtime top up of your line 700000006 of Ksh 71 is successful. 25% bonus airtime received. Bal: Ksh 95.0. Dial *131*1# to check bonus.",
        "airtelmoney", "airtel.airtime.line", 71.0, 0.0, 95.0, "Airtime", "700000006", null,
    ),
    // Bundle
    Sample(
        "94754018800 Confirmed. You have successfully purchased a bundle of Ksh 50 for your line via Airtel Networks Kenya Ltd on 12/12/25 at 11:36 AM. Fee: Ksh 0. Bal: Ksh 202.0.",
        "airtelmoney", "airtel.bundle", 50.0, 0.0, 202.0, "Airtel Networks Kenya Ltd", null, dt(2025, 12, 12, 11, 36),
    ),
    Sample(
        "JK6LM7NO8PQ Confirmed. Bundle purchase successful of Ksh 149 via Airtel Networks Kenya Ltd on 09/01/26 at 08:10 PM. Bal: Ksh 5.0.",
        "airtelmoney", "airtel.bundle", 149.0, 0.0, 5.0, "Airtel Networks Kenya Ltd", null, dt(2026, 1, 9, 20, 10),
    ),
    Sample(
        "LM7NO8PQ9RS Confirmed. You have successfully purchased a bundle of Ksh 89 via Airtel Networks Kenya Ltd on 10/01/26 at 07:25 PM. Fee: Ksh 0. Bal: Ksh 214.0. ",
        "airtelmoney", "airtel.bundle", 89.0, 0.0, 214.0, "Airtel Networks Kenya Ltd", null, dt(2026, 1, 10, 19, 25),
    ),
    // Withdrawal
    Sample(
        "M6J90H86E53 Confirmed. You have withdrawn Ksh 500 from 650299 ACMESHOP EXAMPLEMALL on 04/12/25 at 11:17 AM. Fee: Ksh 25 Bal Ksh 257.0.",
        "airtelmoney", "airtel.withdrawal", 500.0, 25.0, 257.0, "650299 ACMESHOP EXAMPLEMALL", null, dt(2025, 12, 4, 11, 17),
    ),
)
