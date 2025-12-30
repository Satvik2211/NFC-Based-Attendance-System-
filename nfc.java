import javax.smartcardio.*;
import java.util.List;
import java.nio.charset.StandardCharsets;

public class nfc {

    public static void main(String[] args) {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
                System.out.println("No NFC reader found!");
                return;
            }

            CardTerminal terminal = terminals.get(0);
            System.out.println("Reader: " + terminal.getName());

            while (true) {
                System.out.println("\nTap ID card...");

                terminal.waitForCardPresent(0);

                try {
                    Card card = terminal.connect("*");
                    CardChannel channel = card.getBasicChannel();

                    // APDU to read data (NDEF, CC file, etc. varies by tag)
                    // This APDU reads 16 bytes from page 4 (common for NTAG213/215/216)
                    byte[] cmd = new byte[]{
                            (byte) 0xFF, // Class
                            (byte) 0xB0, // Read Binary
                            0x00,        // P1: page number MSB
                            0x04,        // P2: page number LSB (start from page 4)
                            0x10         // Le: read 16 bytes
                    };

                    ResponseAPDU resp = channel.transmit(new CommandAPDU(cmd));
                    byte[] data = resp.getData();

                    if (resp.getSW1() == (byte)0x90 && resp.getSW2() == 0x00) {
                        // Convert raw bytes to text
                        String payload = new String(data, StandardCharsets.UTF_8).trim();

                        System.out.println("Raw NDEF Data (first bytes): " + payload);

                        // Extract roll number (remove weird characters)
                        String roll = payload.replaceAll("[^A-Za-z0-9-]", "");

                        System.out.println("ROLL NUMBER READ: " + roll);
                    } else {
                        System.out.println("Failed to read NDEF data.");
                    }

                    card.disconnect(false);

                } catch (Exception e) {
                    System.out.println("Error reading card:");
                    e.printStackTrace();
                }

                System.out.println("Remove card...");
                terminal.waitForCardAbsent(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
