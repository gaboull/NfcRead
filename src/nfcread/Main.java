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

    private static long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;

        for (int i = 5; i < bytes.length - 2; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }

        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 3; i >= 5; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
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
                    LOG.log(Level.INFO, "Reading terminal {0}", id);
                    cardTerminal.waitForCardPresent(0);

                    Card card = cardTerminal.connect("T=1");
                    CardChannel channel = card.getBasicChannel();
                    CommandAPDU command = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xB0, (byte) 0x00, (byte) 0x04, (byte) 0x04});
                    ResponseAPDU response = channel.transmit(command);
                    LOG.log(Level.INFO, "Data:{0}", toHex(response.getData()));
                    LOG.log(Level.INFO, "Atr :{0}", toDec(card.getATR().getBytes()));
                    card.disconnect(false);

                    cardTerminal.waitForCardAbsent(0);                    
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
