package March.Automative;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.mail.*;
import javax.mail.Session;
import javax.mail.internet.*;
import com.jcraft.jsch.*;
import org.testng.Assert;
import org.testng.annotations.*;

public class Machines_stored {
    private static final int PORT = 22;
    private static final String USER = "appUser";
    private static final String PASSWORD = "Brain@123";

    private static final Map<String, String> MACHINES = new HashMap<>();

    static {
        MACHINES.put("pp1.humanbrain.in", "/store/nvmestorage/postImageProcessor");
        MACHINES.put("pp2.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp3.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp4.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp5.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp7.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("qd4.humanbrain.in", "/mnt/local/nvme2/postImageProcessor");
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("MMM dd").format(new Date()); // Example: Mar 01
    }

    private Map<String, List<String>> getFiles(String host, String directory) {
        List<String> pendingFiles = new ArrayList<>();
        List<String> currentFiles = new ArrayList<>();
        Map<String, List<String>> result = new HashMap<>();

        try {
            JSch jsch = new JSch();
            com.jcraft.jsch.Session sshSession = jsch.getSession(USER, host, PORT);  // Fully qualified for SSH Session
            sshSession.setPassword(PASSWORD);
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect();

            ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
            channelExec.setCommand("ls -lh --time-style='+%b %d %H:%M' " + directory);
            channelExec.setInputStream(null);
            channelExec.setErrStream(System.err);

            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            channelExec.connect();

            String currentDate = getCurrentDate();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(".*[0-9]{2}:[0-9]{2}.*")) { // Ensuring it matches timestamp format
                    if (line.contains(currentDate)) {
                        currentFiles.add(line);
                    } else {
                        pendingFiles.add(line);
                    }
                }
            }

            channelExec.disconnect();
            sshSession.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        result.put("currentFiles", currentFiles);
        result.put("pendingFiles", pendingFiles);
        return result;
    }

    @Test
    public void testFiles() {
        StringBuilder emailBody = new StringBuilder();
        boolean hasPendingFiles = false;

        for (Map.Entry<String, String> entry : MACHINES.entrySet()) {
            String host = entry.getKey();
            String directory = entry.getValue();

            Map<String, List<String>> files = getFiles(host, directory);
            List<String> currentFiles = files.get("currentFiles");
            List<String> pendingFiles = files.get("pendingFiles");

            System.out.println("\n================================== " + host + " ==================================");

            if (!currentFiles.isEmpty()) {
                System.out.println("Current Files:");
                System.out.println("---------------------------------------------------------------------------------------");
                System.out.println("Permissions    Size     Date       Time              Filename");
                System.out.println("---------------------------------------------------------------------------------------");
                for (String file : currentFiles) {
                    printFormattedFile(file);
                }
            } else {
                System.out.println("No new files added today on " + host);
            }

            System.out.println();

            if (!pendingFiles.isEmpty()) {
                hasPendingFiles = true;
                emailBody.append("\n========== ").append(host).append(" ==========\n");
                emailBody.append("Pending Files:\n");
                emailBody.append("Permissions    Size     Date       Time              Filename\n");
                emailBody.append("---------------------------------------------------------------------------------------");

                System.out.println("Pending Files:");
                System.out.println("Permissions    Size     Date       Time              Filename");
                System.out.println("---------------------------------------------------------------------------------------");
                for (String file : pendingFiles) {
                    printFormattedFile(file);
                    emailBody.append(file).append("\n");
                }
            } else {
                System.out.println("No pending files remaining on " + host);
                System.out.println("================================== ***DONE**** ==================================");
            }

            Assert.assertNotNull(files, "Failed to fetch files from " + host);
        }

        // Send email if pending files exist
        if (hasPendingFiles) {
            sendEmail("recipient@example.com", "Pending Files Notification", emailBody.toString());
        }
    }

    private void printFormattedFile(String file) {
        String[] parts = file.split("\\s+");
        if (parts.length >= 9) {
            String permissions = parts[0];
            String size = parts[4];
            String date = parts[5] + " " + parts[6];
            String time = parts[7];
            String filename = String.join(" ", Arrays.copyOfRange(parts, 8, parts.length));

            System.out.printf("%-14s %-8s %-10s %-6s %s%n", permissions, size, date, time, filename);
        }
    }

    public static void sendEmail(String toEmail, String subject, String bodyContent) {
        final String fromEmail = "softwaretestingteam9@gmail.com"; // Your Gmail address
        final String appPassword = "0999"; // Your generated App Password

        // SMTP server settings
        String host = "smtp.gmail.com";
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true"); // Start TLS

        // Get the session object with authentication
        Session session = Session.getInstance(properties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, appPassword); // Use App Password here
            }
        });

        try {
            // Compose the message
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject(subject);
            message.setText(bodyContent);

            // Send the message
            Transport.send(message);
            System.out.println("Email sent successfully");
        } catch (MessagingException e) {
            e.printStackTrace();
            System.out.println("Failed to send email: " + e.getMessage());
        }
    }
}
