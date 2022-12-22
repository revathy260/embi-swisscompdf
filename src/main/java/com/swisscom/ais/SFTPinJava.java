package com.swisscom.ais;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;

//import android.util.Base64;
import java.util.Base64;

@RestController
@RequestMapping(value = "/demo")
public class SFTPinJava {
    // create GET endpoint to serve demo data at /demo/data
    @GetMapping(value = "/data")
    public String getDemoData() {
        return "Demo Data";
    }
    public static void main(String[] args) {
        String SFTPHOST = "mft.outline.ch";
        int SFTPPORT = 22;
        String SFTPUSER = "gen.int";
        String privateKey = "/Users/revathy/Documents/FTPServer/generalitestPrivate.pem";
        String SFTPWORKINGDIR = "/Home/gen.int";
        JSch jSch = new JSch();
        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;
        try {
            jSch.addIdentity(privateKey);
            System.out.println("Private Key Added.");
            session = jSch.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
            System.out.println("session created.");
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel("sftp");
            channel.connect();
            System.out.println("shell channel connected....");
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(SFTPWORKINGDIR);
            System.out.println("Changed the directory...");

            String sourcePath = "/Home/gen.int/sample.txt";
            String destinationPath = "/Users/revathy/Documents/FTPServer";
            channelSftp.get(sourcePath, destinationPath);
            System.out.println("Downloaded file successfully");

            String localFile = "/Users/revathy/Documents/FTPServer/EMBIAPIOnHeroku.pdf";
            String remoteDir = "/Home/gen.int/";
            channelSftp.put(localFile, remoteDir + "_HOST.pdf");
            System.out.println("File transferred successfully to host using local dir.");

            File file = new File(localFile);
            byte[] buffer = new byte[(int) file.length() + 100];
            @SuppressWarnings("resource")
            int length = new FileInputStream(file).read(buffer);
            String base64 = new String(Base64.getEncoder().encode(buffer));
            byte[] asBytes = Base64.getDecoder().decode(base64);
            InputStream is = new ByteArrayInputStream(asBytes);
            channelSftp.put(is, remoteDir + "01_HOST.pdf");
            System.out.println("File transferred successfully to host via base64.");


            //String base64 = Base64.encodeToString(buffer, 0, length,Base64.DEFAULT);
            //byte[] data1 = Base64.decodeBase64(base64);
            //byte[] data = Base64.decodeBase64(base64);
            //byte[] data = BaseEncoding.base64().decode(base64);
            //System.out.println(base64);
            //byte[] fileContent = FileUtils.readFileToByteArray(new File(filePath));
            //String encodedString = Base64.getEncoder().encodeToString(fileContent);
            //BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
            //channelSftp.put(new FileInputStream(), remoteDir + "_HOST.pdf");
            //System.out.println("File transferred successfully to host.");

        } catch (JSchException e) {
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (channelSftp != null) {
                channelSftp.disconnect();
                channelSftp.exit();
            }
            if (channel != null)
                channel.disconnect();
            if (session != null)
                session.disconnect();
        }
    }
}
