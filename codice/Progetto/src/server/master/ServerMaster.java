package server.master;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Random;

import common.Configs;
import common.DfsException;
import server.replica.ServerReplicaInterface;

/**
 * Classe per la gestione del ServerMaster.
 * 
 * Si occupa di ricevere le richieste dei client, controllare quali ServerReplica sono disponibili così da sceglierne uno 
 * per poi comunicare il relativo ID al client come risposta in modo che il client possa comunicare direttamente con
 * lui.
 * Quale ServerReplica viene scelto? Per questo progetto, il ServerReplica è scelto random tra quelli disponibili / raggiungibili
 * ma in relatà possono esserci diversi criteri come ad esempio di sceglie la replica più 'vicino' in senso geografico al
 * client che effettua la richiesta.
 * 
 * Si occupa inoltre di gestire la concorrenza sulle risorse del filesystem tramite la classe LockManager.
 * Ogni volta che avviene lo start di un' operazione, viene settato il lock in scrittura (se l'operazione è di scrittura) 
 * oppure viene aggiunto un reader (nel caso in cui si acceda in lettura). 
 * In maniera equivalente viene tolto il lock in scrittura (o tolto un reader se accedo in lettura) nel momento in cui 
 * avviene l'end dell'operazione.
 * 
 * @author gabrielesavoia
 *
 */
public class ServerMaster extends UnicastRemoteObject implements ServerMasterInterface{
	
	private static final long serialVersionUID = 1L;
	
	private LockManager lockManager;
	
	private int numReplicas;
	private ServerReplicaInterface[] replicas;
	
	/**
	 * Costruttore della classe.
	 * 
	 * Effettuo il lookup di ogni ServerReplica, poi per ciascuno chiamo lookupOtherReplicas().
	 * 
	 * E' necessario che tutti (numReplicas) ServerReplica siano attivi.
	 * 
	 * @param numReplicas Numero di ServerReplica nel sistema
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	public ServerMaster(int numReplicas) throws RemoteException, DfsException {
		super();
		
		lockManager = new LockManager();
		this.numReplicas = numReplicas;
		
		// lookup dei ServerReplica e per ciscuno chiamo lookupOtherReplicas()
		try {
			replicas = new ServerReplicaInterface[this.numReplicas];
			for (int i=0; i<this.numReplicas; i++) {
				replicas[i] = (ServerReplicaInterface) Naming.lookup (Configs.URL+"ReplicaServer"+i);
				replicas[i].lookupOtherReplicas(this.numReplicas);
			}
		}
		catch(Exception e) {
			throw new DfsException("Errore: non è possibile fare il lookup con almeno uno dei "+this.numReplicas+" ServerReplica", true);
		}
		
	}
	
	/**
	 * Funzione che deve essere chiamata quando si vuole interagire con il filesystem.
	 * E' necessario specificare se si tratta di un'operazione di scrittura ('w) o lettura ('r) e in relazione
	 * a quale path.
	 * 
	 * Nel caso in cui vengano rilevati problemi di concorrenza, viene generata un'eccezione.
	 * Ritorna l'ID del ServerReplica con cui il client dovrà comunicare per eseguire effettivamente l'operazione.
	 * 
	 * @param lockType 'w' per operazione in scrittura, 'r' per operazione in lettura
	 * @param path su cui fare l'operazione
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per problemi di concorrenza
	 */
	@Override
	public int startOperation(char lockType, String path) throws RemoteException, DfsException {
		
		switch(lockType) {
			case('w'):
				// write lock
				if (!lockManager.writeLock(path)) { 
					throw new DfsException("Errore: qualcuno ha già il write lock (oppure c'è almeno 1 reader) e non è possibile modificare, riprova"); 
				}
				break;
			case('r'):
				// aggiungi reader
				if (!lockManager.addReader(path)) { 
					throw new DfsException("Errore: qualcuno ha già il write lock e non è possibile accedere, riprova"); 
				}
				break;
			default: throw new DfsException("Errore: lockType non ammesso", true);
		}
		
		return getServerReplicaId();
		
	}
	
	/**
	 * Funzione che deve essere chiamata quando l'operazione è terminata.
	 * 
	 * Vengono tolti i lock in scrittura (oppure viene eliminato il reader per operazioni in lettura).
	 * 
	 * @param lockType 'w' per operazione in scrittura, 'r' per operazione in lettura
	 * @param path su cui è stata fatta l'operazione
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per problemi di concorrenza
	 */
	@Override
	public void endOperation(char lockType, String path) throws RemoteException, DfsException {
		
		switch(lockType) {
			case('w'):
				// write unlock
				if (!lockManager.writeUnlock(path)) { 
					throw new DfsException("Errore: non è possibile fare unlock", true); 
				}
				break;
			case('r'):
				// elimina reader
				if (!lockManager.delReader(path)) { 
					throw new DfsException("Errore: non è possibile togliere il reader", true);
				}
				break;
			default: throw new DfsException("Errore: lockType non ammesso", true);
		}
		
	}
	
	/**
	 * Funzione che ritorna il numero di ServerReplica.
	 * 
	 * @return Numero di ServerReplica
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 */
	@Override
	public int getNumServerReplica() throws RemoteException {
		
		return numReplicas;
		
	}
	
	/**
	 * Ritorna l'id del ServerReplica che contatterà il client.
	 * Quale ServerReplica è scelto? Viene scelto casualmente il primo ServerReplica raggiungibile.
	 * 
	 * Al posto di sceglierlo a caso si potrebbero fare considerazioni del tipo "ritorno l'id del ServerReplica più vicino
	 * al client" oppure "ritorno l'id del ServerReplica meno occupato".
	 * 
	 * @throws DfsException Generata se non ci sono più ServerReplica disponibili
	 */
	private int getServerReplicaId() throws DfsException {
		
		int id = new Random().nextInt(numReplicas);
		int[] failedList = new int[numReplicas];
		Arrays.fill(failedList, 0);
		
		boolean foundServerReplicaAlive = false;
		while(!foundServerReplicaAlive) {
			
			try { foundServerReplicaAlive = replicas[id].isAlive(); }
			catch (RemoteException e){  // se il ServerReplica non è raggiungibile
			
				failedList[id] = 1;
				
				// se nessun ServerReplica è raggiungibile genero eccezione
				int numFailed = 0;
				for (int i=0; i<failedList.length; i++) { numFailed += failedList[i]; }
				if (numFailed == numReplicas) {
					throw new DfsException("Errore: nessun ServerReplica è raggiungibile", true);
				}
				
				// trovo nuovo id random non ancora considerato
				while(failedList[id]==1) { id = new Random().nextInt(numReplicas); }
			
			}
		}
		
		return id;
	}

}
