package nfcread;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 *
 * @author gabo
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static String readableHex(byte[] src, int from, int length) {
        StringBuilder answer = new StringBuilder();
        for (int i = from; i < from + length; i++) {
            answer.append(String.format("%02X", src[i]));
        }
        return answer.toString();
    }

    private static String readableDec(byte[] src, int from, int length) {
        StringBuilder answer = new StringBuilder();
        for (int i = from; i < from + length; i++) {
            answer.append(String.format("%02d ", src[i] & 0xFFFFFFFFL));
        }
        return answer.toString();
    }
    
    private static long toLong(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 5; i < bytes.length-2; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }    
    
    private static void init() throws ClassNotFoundException, NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class pcscterminal = Class.forName("sun.security.smartcardio.PCSCTerminals");
        Field contextId = pcscterminal.getDeclaredField("contextId");
        contextId.setAccessible(true);

        if (contextId.getLong(pcscterminal) != 0L) {
            Class pcsc = Class.forName("sun.security.smartcardio.PCSC");

            Method SCardEstablishContext = pcsc.getDeclaredMethod("SCardEstablishContext", new Class[]{Integer.TYPE});
            SCardEstablishContext.setAccessible(true);

            Field SCARD_SCOPE_USER = pcsc.getDeclaredField("SCARD_SCOPE_USER");
            SCARD_SCOPE_USER.setAccessible(true);

            long newId = ((Long) SCardEstablishContext.invoke(pcsc, new Object[]{SCARD_SCOPE_USER.getInt(pcsc)}));
            contextId.setLong(pcscterminal, newId);
        }
    }

    public static void listTerminals() {
        try {
            TerminalFactory terminalFactory = TerminalFactory.getDefault();
            List<CardTerminal> cardTerminalList = terminalFactory.terminals().list();

            LOG.log(Level.INFO, "Found {0} terminals:", cardTerminalList.size());
            for (CardTerminal cardTerminal : cardTerminalList) {
                LOG.info(cardTerminal.getName());
            }
        } catch (CardException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    public static void readTerminal(int id, boolean endless) {
        try {
            TerminalFactory terminalFactory = TerminalFactory.getDefault();
            List<CardTerminal> cardTerminalList = terminalFactory.terminals().list();
            if (id <= cardTerminalList.size()) {
                CardTerminal cardTerminal = cardTerminalList.get(id - 1);
                do {                    
                    LOG.log(Level.INFO, "Waiting for terminal {0}", id);
                    cardTerminal.waitForCardPresent(0);

                    LOG.log(Level.INFO, "Card present, reading terminal {0}", id);
                    Card card = cardTerminal.connect("*");
                    CardChannel channel = card.getBasicChannel();
                    // Read UUID
                    //CommandAPDU command = new CommandAPDU(new byte[]{(byte) 0xff, (byte) 0xca, 0, 0, 0});
                    // Read binary block
                    CommandAPDU command = new CommandAPDU(new byte[]{(byte) 0xff, (byte) 0xb0, (byte) 0x00, (byte) 0x04, (byte) 0x04});
                    /*ResponseAPDU response = */channel.transmit(command);
                    byte[] uidBytes = card.getATR().getBytes(); //response.getData();
                    //LOG.log(Level.INFO, "Hex Readed UID:{0}", readableHex(uidBytes, 0, 4));
                    //LOG.log(Level.INFO, "Dec Readed UID:{0}", readableDec(uidBytes, 0, 4));
                    LOG.log(Level.INFO, "LONG DATA:" + toLong(uidBytes));
                    card.disconnect(false);

                    LOG.log(Level.INFO, "Waiting for card absent on terminal {0}", id);
                    cardTerminal.waitForCardAbsent(0);                    
                    LOG.log(Level.INFO, "Card absent on terminal {0}", id);
                } while (endless);
            }
        } catch (CardException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            init();
            listTerminals();
            if (args.length > 0) {
                int terminalId = Integer.parseInt(args[0]);
                if (terminalId > 0 && terminalId < 3) {
                    readTerminal(terminalId, false);
                } else if (terminalId > 2 && terminalId < 5) {
                    readTerminal(terminalId - 2, true);
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

}
