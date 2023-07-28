package server.replica;
import java.rmi.Remote;
import java.rmi.RemoteException;

import common.DfsException;

/**
 * @author gabrielesavoia
 * 
 * Questa è l'interfaccia remota (deve estendere Remote), ovvero l'interfaccia che contiene i metodi da 
 * chiamare sull'oggetto remoto.
 *
 */
public interface ServerReplicaInterface extends Remote {
	
	void createFile(String path, boolean needPropagate) throws RemoteException, DfsException;
	
	void writeFile(String path, byte[] content, boolean needPropagate) throws RemoteException, DfsException;

	byte[] readFile(String path) throws RemoteException, DfsException;
	
	void remove(String path, boolean needPropagate) throws RemoteException, DfsException;

	void moveFile(String sourcePath, String targetPath, boolean needPropagate) throws RemoteException, DfsException;

	void renameFile(String path, String newName, boolean needPropagate) throws RemoteException, DfsException;
	
	void createDirectory(String path, boolean needPropagate) throws RemoteException, DfsException;
	
	String[] listFilesDirectory(String directoryPath) throws RemoteException, DfsException;
	
	void lookupOtherReplicas(int numReplicas) throws RemoteException, DfsException;
	
	boolean isAlive() throws RemoteException;
}
