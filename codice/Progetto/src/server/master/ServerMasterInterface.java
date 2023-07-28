package server.master;

import java.rmi.Remote;
import java.rmi.RemoteException;

import common.DfsException;

public interface ServerMasterInterface extends Remote{
	
	int startOperation(char lockType, String path) throws RemoteException, DfsException;
	void endOperation(char lockType, String path) throws RemoteException, DfsException;
	
	int getNumServerReplica() throws RemoteException;

}
