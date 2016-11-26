package sirs.remotedocs.demos;

import java.util.logging.Level;
import java.util.logging.Logger;
import static javax.xml.bind.DatatypeConverter.printHexBinary;

import sirs.remotedocs.ClientImplementation;
import types.Buffer_t;
import utils.CryptoUtils;

/*  Demo Class used for demonstrating a client connecting to the File Server, 
    and issuing a write and read command on his file.

    Please refer to the output generated by the server at runtime as it
    receives and processes the Demo Apps requests, for information on the 
    exceptions caught by the file system, and the returns of the operations 
    being performed by the server.
 */
public class DemoReadWriteOwnFile {

    public static void main(String[] args) {
        try {
            ClientImplementation c = new ClientImplementation();
            Buffer_t buffer = new Buffer_t(CryptoUtils.serialize(""));
            
            // Initializing the file system
            System.out.println("Initializing the File System...");
            c.fs_init();
            System.out.println("Done!");
            System.out.println("Client ID assigned by server: " + c.getClientID().getValue());
            System.out.println("---------------------------------------------------------\n");
            
            // Writing to the file at position 0
            String s = "The quick brown fox jumps over the lazy dog";
            buffer.setValue(CryptoUtils.serialize(s));
            System.out.println("Writing some data of size " + buffer.getValue().length + "to the file, at pos 0 ...");
            c.fs_write(0, buffer.getValue().length, buffer);
            String sent = printHexBinary(buffer.getValue());
            System.out.println("Done!");
            System.out.println("Data sent to the file system:  " + sent);
            System.out.println("---------------------------------------------------------\n"); 
            
            // Reading all the data that was just written to the file
            System.out.println("Reading the data that was just written to the file...");
            int bytesRead = c.fs_read(c.getClientID(), 0, buffer.getValue().length, buffer);
            String received = printHexBinary(buffer.getValue());
            System.out.println("Done!");
            System.out.println("Number of bytes that were read: " + bytesRead);
            System.out.println("Data read from the file:  " + received);
            System.out.println("---------------------------------------------------------\n");            
            
            // Comparing the output of read and write
            System.out.println("Comparing the data sent with the data received...");
            System.out.println("String.compareTo(sent,received) returns:" + sent.compareTo(received));
            System.out.println("---------------------------------------------------------\n");            
            
        } catch (Exception ex) {
            Logger.getLogger(DemoReadWriteOwnFile.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
