package server.master;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.Scanner;

import common.Configs;
import common.DfsException;

/**
 * Comandi per l'esecuzione:	
 * 		$ cd ~/eclipse-workspace/Progetto/bin	
 * 		$ java server.master.ServerMasterRun
 * 
 * IMPORTANTE: è necessario che il registry sia attivo. Se non è attivo esegui:
 * 		$ cd ~/eclipse-workspace/Progetto/bin
 * 		$ rmiregistry
 * 
 * Si occupa di far eseguire il ServerMaster.
 * E' necessario specificare il numero di ServerReplica che si intende utilizzare.
 * 
 * @author gabrielesavoia
 *
 */
public class ServerMasterRun {

	public static void main(String[] args) {
		
		ServerMasterInterface serverMaster = null;
		
		int numReplicas = 0;
		Scanner scanner = new Scanner(System.in);
		boolean successCreation = false;
		
		while( !successCreation ) {
			try {
				System.out.println("Immetti il numero di ServerReplica da utilizzare:");
				String[] strNumReplicas = scanner.nextLine().split(" ");
				if (strNumReplicas.length == 0) { continue; }
				numReplicas = Integer.parseInt(strNumReplicas[0]);
				if (numReplicas <=0 ) {
					System.out.println("E' necessario inserire un nuovero di ServerReplica > 0");
					continue;
				}
				serverMaster = new ServerMaster(numReplicas);
				successCreation = true;
				System.out.println("Lookup dei "+numReplicas+" ServerReplica avvenuto con successo"); 
			} catch (RemoteException e) {
				System.out.println("Errore creazione ServerMaster: problemi di connessione");
				System.exit(1);
			}catch (DfsException e) {
				System.out.println(e.getMessage());
				System.exit(1);
			}catch (NumberFormatException e) {
				System.out.println("Errore: il numero di ServerReplica deve essere un numero intero");
			}
		}
		scanner.close();
		
		// Registrazione oggetto remoto nel registry
		try {
			Naming.rebind(Configs.URL+"ServerMaster", serverMaster);
			System.out.println("ServerMaster correttamente registrato nel Registry");
		}
		catch (Exception e){
			System.out.println("Problemi durante la registrazione del ServerMaster nel Registry");
			System.exit(1);
		}
		
		System.out.println("ServerMaster pronto a ricevere richieste");

	}

}
