package com.swisscom.ais;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
public class SFTPinJava {
    public static void main(String[] args) {
        String SFTPHOST = "mft.outline.ch";
        int SFTPPORT = 22;
        String SFTPUSER = "gen.int";
        String privateKey = "/Users/revathy/Documents/FTPServer/generalitestPrivate.pem";
        String SFTPWORKINGDIR = "/Users/revathy/Documents/FTPServer";
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
            /* channelSftp = (ChannelSftp) channel;
            channelSftp.cd(SFTPWORKINGDIR);
            System.out.println("Changed the directory..."); */
        } catch (JSchException e) {
            e.printStackTrace();
        } /*catch (SftpException e) {
            e.printStackTrace();
        }*/ finally {
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
