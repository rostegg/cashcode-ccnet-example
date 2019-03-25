package com.rostegg.cashcode;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

import java.util.Arrays;

public class CashcodeCcnet {
    /*
        code,data -> command | response
        0x37,[] -> identificator | 3 arrays
        0x30,[] -> reset | array
        0x35,[] -> stack | array
        0xFF, [] -> sendNSC | -
        0x00, [] -> sendASC | -
        0x31, [] -> getStatus | array
        0x32,[0,0,BillTypesByte] -> setSecurity, BillTypesByte - denomination of bills | -
        0x34,[0,0,BillTypesByte,0,0,0] -> enableBillTypes, BillTypesByte - denomination of bills | -
        0x36, [] -> return | -
        0x33, [] -> poll | -
    */


    private final int POLYNOMIAL = 0x08408;
    private final int DELAY = 350;

    private volatile boolean isLoop = false;

    private SerialPort serialPort;

    public CashcodeCcnet(String portName) {
        serialPort = new SerialPort(portName);
    }


    public void startAccept() throws SerialPortException, InterruptedException {
        serialPort.openPort();

        serialPort.setParams(SerialPort.BAUDRATE_9600,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE);
        serialPort.addEventListener(new PortReader(), SerialPort.MASK_RXCHAR);

        sendReset();

        /*
            here are the values of the banknotes that the bill acceptor can accept
            it all depends on the firmware of the bill acceptor, it can be stitched on various bills
            for example, for Ukrainian hryvnia values looked like this:
            five-hryvnia - 00000100 - 4
            ten-hryvnia - 00001000 - 8
            ...
         */
        int cash = 4+8+16+32+64;
        sendEnableBillTypes(cash);
        startPollingLoop();
    }


    public boolean stop() throws SerialPortException {
        stopPolling();
        boolean result = serialPort.isOpened() ? serialPort.closePort() : false;
        return result;
    }

    public void pause() throws InterruptedException {
        Thread.sleep(DELAY);
    }

    public void startPollingLoop() throws SerialPortException, InterruptedException {
        isLoop = true;
        while (isLoop)
            sendPoll();
    }

    public void stopPolling() {
        isLoop = false;
    }

    public void sendNsc() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0xFF,new int[]{}));
    }

    public void sendAsc() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x00,new int[]{}));
    }

    public void sendStack() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x35,new int[]{}));
    }

    public void sendReset() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x30,new int[]{}));
    }

    // 3 arrays return
    public void sendIdent() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x37,new int[]{}));
    }

    public void sendStatus() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x31,new int[]{}));
    }
    public void sendSecurity(int value) throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x32,new int[]{0,0,value}));
    }

    public void sendEnableBillTypes(int value) throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x34,new int[]{0,0,0x7C,0,0,0}));
    }

    public void sendReturn() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x36,new int[]{}));
    }

    public void sendPoll() throws SerialPortException, InterruptedException {
        sendPacket(formPacket(0x33,new int[]{}));
    }

    private void sendPacket(int[] packet) throws SerialPortException, InterruptedException {
        serialPort.writeIntArray(packet);
        pause();
    }

    private int[] formPacket(int command, int[]data) {
        int length = data.length + 6;
        int [] commandArr = new int[256];
        commandArr[0] = 0x02;   //sync
        commandArr[1] = 0x03;   //valid address
        commandArr[2] = length; //length
        commandArr[3] = command; //command

        if (data.length != 0) {
            int i = 4, d=0;
            while (d != data.length) {
                commandArr[i] = data[d];
                i+=1;
                d+=1;
            }
        }
        int []crcPacket =  Arrays.copyOfRange(commandArr, 0, length-2);
        int crcValue = getCrc16(crcPacket);
        commandArr[length-1]=(crcValue >> 8 ) & 0xFF;
        commandArr[length-2]=crcValue  & 0xFF;
        int []res = Arrays.copyOfRange(commandArr, 0, length);
        return  res;
    }

    private int getCrc16(int []arr) {
        int i, tmpCrc=0;
        byte j;
        for(i = 0; i <= arr.length-1; i++) {
            tmpCrc^=arr[i];
            for (j=0; j <= 7;j++) {
                if ((tmpCrc & 0x0001) != 0) {
                    tmpCrc>>=1;
                    tmpCrc^=POLYNOMIAL;
                } else {
                    tmpCrc>>=1;
                }
            }
        }
        return  tmpCrc;
    }

    private class PortReader implements SerialPortEventListener {
        public void serialEvent(SerialPortEvent event) {
            if(event.isRXCHAR() && event.getEventValue() > 0) {
                int [] receivedData = new int[0];
                try {
                    receivedData = serialPort.readIntArray();
                } catch (SerialPortException e) {
                    e.printStackTrace();
                }
                // here parse packet as you want
                System.out.println(Arrays.toString(receivedData));
            }
        }

    }
}
