package embedded.mas.bridges.javard;

import java.io.IOException;
import java.io.InputStream;

import arduino.NRJ;
import embedded.mas.bridges.jacamo.EmbeddedAction;
import embedded.mas.bridges.jacamo.IPhysicalInterface;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

public class NRJ4EmbeddedMas extends NRJ implements IPhysicalInterface {

    private String preamble = "==";
    private String startMessage = "::";
    private String endMessage = "--";
    private boolean connected = false;

    public NRJ4EmbeddedMas(String portDescription, int baud_rate)
            throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException {
        super(portDescription, baud_rate);
        this.openConnection();
    }

    @SuppressWarnings("unchecked")
    public String read() {
        return this.serialRead();
    }

    @Override
    public boolean write(String s) {
        try {
            serialWrite(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void closeConnection() {
        super.closeConnection();
        this.connected = false;
    }

    @Override
    public boolean openConnection() {
        if (!connected) {
            this.connected = super.openConnection();
        }
        return this.connected;
    }

    @Override
    public String serialRead() {
        try {
            if (comPort.getInputStream().available() == 0) return "";

            String s = "";
            String start = "";
            String end = "";

            comPort.enableReceiveTimeout(100);
            byte[] b = new byte[1];

            InputStream in = comPort.getInputStream();
            int data;
            while (!start.equals(preamble)) {
                data = in.read();
                if ((char) data == preamble.charAt(start.length())) {
                    start = start + (char) data;
                } else {
                    start = "";
                }
            }

            while (!end.equals(endMessage)) {
                data = in.read();
                if ((char) data == endMessage.charAt(end.length())) {
                    end = end + (char) data;
                } else {
                    s = s + end + (char) data;
                    end = "";
                }
            }

            System.out.println("lendo " + s);
            String[] strings = s.split(startMessage);
            System.out.println("lendo (2) " + s);
            int number = Integer.parseInt(strings[0]);
            String message = strings[1];

            if (number == message.length()) {
                System.out.println("Leu: " + message);
                return message;
            } else {
                System.out.println("Message conversation error " + message);
                for (int i = 0; i < message.length(); i++) {
                    int teste = message.charAt(i);
                    System.out.println(message.charAt(i) + " - " + teste);
                }
                return "Message conversation error";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Message conversation error";
        } catch (UnsupportedCommOperationException e) {
            e.printStackTrace();
            return "Message conversation error";
        }
    }

    @Override
    public void execEmbeddedAction(EmbeddedAction action) {
        System.err.println("Method execEmbeddedAction not implemented in " + this.getClass().getName());
    }
}
