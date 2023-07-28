package server.replica;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.Scanner;

import common.Configs;
import common.DfsException;

/**
 * Comandi per l'esecuzione:	
 * 		$ cd ~/eclipse-workspace/Progetto/bin	
 * 		$ java server.replica.ServerReplicaRun
 * 
 * IMPORTANTE: è necessario che il registry sia attivo. Se non è attivo esegui:
 * 		$ cd ~/eclipse-workspace/Progetto/bin
 * 		$ rmiregistry
 * 
 * Si occupa di far eseguire un ServerReplica.
 * Una volta eseguito sarà richiesto di specificare un ID.
 * Il vincolo è che l'ID è incrementale e univoco tra tutti i ServerReplica (parte da 0).
 * 
 * Di default devono essere eseguiti 3 ServerReplica.
 * 
 * @author gabrielesavoia
 *
 */
public class ServerReplicaRun {

	public static void main(String[] args) throws RemoteException {
		
		String defaultReplicaPoint = "/Users/gabrielesavoia/Documents/progettoDistrComp/replica";
		
		ServerReplicaInterface replicaServer = null;
		String[] replicaId = null;
		Scanner scanner = new Scanner(System.in);
		boolean successCreation = false;
		
		while( !successCreation ) {
			try {
				System.out.println("Immetti ID del ServerReplica (sono accettati solo valori interi):");
				replicaId = scanner.nextLine().split(" ");
				if (replicaId.length == 0) { continue; }
				Integer.parseInt(replicaId[0]);
				replicaServer = new ServerReplica(defaultReplicaPoint, replicaId[0]);
				successCreation = true;
				System.out.println("ServerReplica correttamente creato con replica path: "+defaultReplicaPoint+replicaId[0]); 
			} catch (DfsException e) {
				System.out.println(e.getMessage());
			} catch (NumberFormatException e) {
				System.out.println("Errore: ID deve essere un numero intero");
			} catch (RemoteException e) {
				System.out.println("Errore creazione ServerReplica: problemi di connessione");
				scanner.close();
				System.exit(1);
			}
		}
		scanner.close();
		
		// Registrazione oggetto remoto nel registry
		try {
			Naming.rebind(Configs.URL+"ReplicaServer"+replicaId[0], replicaServer);
			System.out.println("ServerReplica correttamente registrato nel Registry");
		}
		catch (Exception e){
			System.out.println("Problemi durante la registrazione del ServerReplica nel Registry");
			System.exit(1);
		}
		
		System.out.println("ServerReplica pronto a ricevere richieste");

	}

}
