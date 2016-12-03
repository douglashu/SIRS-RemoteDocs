package sirs.remotedocs;

import interfaces.InterfaceBlockServer;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.*;
import java.security.spec.InvalidKeySpecException;

import types.*;
import utils.HashUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import static utils.CryptoUtils.*;

public class ClientImplementation {

    private String clientUsername;
    private ClientBox_t clientBox;
    private byte[] clientSalt;
    private SecretKey clientKey;

    private static InterfaceBlockServer server;


    public ClientImplementation() {
        try {
            connectToServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setClientUsername(String username){
        clientUsername = username;
    }

    public String getClientUsername() {
        return clientUsername;
    }

    public void setClientBox(ClientBox_t clientBox){
        this.clientBox = clientBox;
    }

    public void setClientSalt(byte[] clientSalt) {
        this.clientSalt = clientSalt;
    }

    public byte[] getClientSalt() {
        return clientSalt;
    }

    public ClientBox_t getClientBox(){
        return clientBox;
    }

    public boolean connectToServer() throws Exception{
        Registry myReg = LocateRegistry.getRegistry("localhost");
        server = (InterfaceBlockServer) myReg.lookup("rdocs.Server");
        System.out.println(server.greeting() + "\n");
        return isConnected();
    }


    public boolean isConnected(){
        return server != null;
    }

    //Account's Operations
    public int register(String username, String password) {
        System.out.println("user: " + username);
        System.out.println("pw:   " + password);

        if(username.length()<3 || username.length()>21){
            System.out.println("Username must be between 4 and 20 characters long.");
            return 2;
        }

        if(password.length()<7 || password.length()>65){
            System.out.println("Password must be between 8 and 64 characters long.");
            return 3;
        }

        try {
            if(!server.usernameExists(username)) {
                //new ClientBox
                ClientBox_t clientBox = new ClientBox_t(username);

                //TODO make sure password is strong and find a way to use salted pw's

                byte[] salt = getSalt();

                //Cipher client box with symmetric key obtained
                SecretKey clientSecretKey = getSecretKey(password, salt);

                //using username as initialization vector
                byte[] encryptedBox = encrypt(clientSecretKey,salt,clientBox);

                System.out.println("DATA SENT (encrypted empty box): " + clientBox.toString() + "\n");

                server.storeClientBox(username, salt, encryptedBox);
                return 0;
            } else{
                System.out.println("Username already exists");
                return 1;
            }
        } catch (NullPointerException e){
            e.printStackTrace();
            System.out.println("Server is offline.");
            return -1;
        } catch ( InvalidKeySpecException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | IOException
                | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            return -2;
        }

    }

    public int login (String username, String password) throws Exception{
        System.out.println("Logging " + username);
        try {
            if(server.usernameExists(username)){
                setClientSalt(server.getClientSalt(username));
                setClientKey(getSecretKey(password, getClientSalt()));

                //downloads encrypted box from server
                byte[] encryptedBox = server.getClientBox(username);

                //if can't decrypt the box means wrong password
                setClientBox((ClientBox_t) decrypt(getClientKey(), getClientSalt(), encryptedBox));
                setClientUsername(username);
                return 0;
            }
            System.out.println("Username does not exist.");
            return 2;
        } catch (NullPointerException| RemoteException e){
            e.printStackTrace();
            System.out.println("Server is offline.");
            return -1;
        } catch (BadPaddingException e) {
            e.printStackTrace();
            System.out.println("Wrong password.");
            return 1;
        }
    }

    public void resendClientBox() throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        byte[] encryptedClientBox = encrypt(getClientKey(), getClientSalt(), getClientBox());
        server.storeClientBox(getClientUsername(), getClientSalt(), encryptedClientBox);
    }

    public void logout(){
        setClientBox(null);
        setClientUsername("");
        setClientKey(null);
    }

    //Document's Operations
    public Document_t createDocument(String documentTitle) throws NoSuchAlgorithmException, IOException {
        Document_t document = new Document_t(documentTitle, this.getClientUsername());
        if(clientBox.getDocumentsIDSet().contains(document.getDocID())){
            System.out.println("Document with same title already exists");
            return null;
        }
        SecretKey key = getRandomSecretKey();
        clientBox.addDocument(document.getDocID(), document.getOwner(), key);
        uploadDocument(document);
        return document;

    }

    public boolean renewDocumentKey(Document_t document){
        try {
            getClientBox().changeDocumentKey(document.getDocID(), getRandomSecretKey());
            return true;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean uploadDocument(Document_t document){
        try {
            if (document==null) {
                throw new NullPointerException("document is null");
            }

            SecretKey key = getClientBox().getDocumentKey(document.getDocID());
            String hashedDocID = HashUtils.hashInText(document.getDocID() + "&&" + document.getOwner(), null);
            byte[] encryptedDocument = encrypt(key, getClientSalt(), document);
            server.storeDocument(hashedDocID, encryptedDocument);
            resendClientBox();
            return true;
        } catch ( NoSuchAlgorithmException | IOException | NoSuchPaddingException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | NullPointerException e) {
            e.printStackTrace();
            return false;
        }

    }

    public Document_t downloadDocument(String documentID, String owner){
        try {
            SecretKey documentKey = getClientBox().getDocumentKey(documentID);
            String hashedDocID = HashUtils.hashInText(documentID + "&&" + owner, null);
            byte[] encryptedDocument = server.getDocument(hashedDocID);
            return (Document_t) decrypt(documentKey, getClientSalt(), encryptedDocument);

        } catch ( ClassNotFoundException | BadPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException
                | InvalidKeyException | IOException | NoSuchPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean removeDocument(String documentID){
        try {
            getClientBox().removeDocument(documentID);
            resendClientBox();
            String hashedDocID = HashUtils.hashInText(documentID + "&&" + getClientBox().getOwnerID(), null);
            return server.removeDocument(hashedDocID);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //TODO remove, just for testing
    public static void main(String[] args)  throws Exception{

        ClientImplementation client = new ClientImplementation();
        client.register("Hello", "helloworld");
        client.login("Hello", "helloworld");
        System.out.println("list of docs:");
        client.getClientBox().print();

        Document_t doc = client.createDocument("title example");
        Document_t doc3 = client.createDocument("title example 3");
        doc.setContent("example content!");
        doc3.setContent("example content 3");

        client.uploadDocument(doc);

        client.uploadDocument(doc3);

        doc.print();

        doc3.print();


        Document_t docServer =client.downloadDocument(doc.getDocID(), client.getClientUsername());
        Document_t doc3Server =client.downloadDocument(doc.getDocID(), client.getClientUsername());

        docServer.print();

        doc3Server.print();

        client.getClientBox().print();

    }

    public SecretKey getClientKey() {
        return clientKey;
    }

    public void setClientKey(SecretKey secretKey) {
        this.clientKey = secretKey;
    }
}
