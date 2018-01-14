package com.rostegg.cashcode;

public class App 
{
    public static void main( String[] args ) throws Exception {
        CashcodeCcnet cashcode = new CashcodeCcnet("/dev/ttyS0");
        cashcode.startAccept();
    }
}
