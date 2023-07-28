package client;

import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.RemoteException;

import common.Configs;
import common.DfsException;
import server.master.ServerMasterInterface;
import server.replica.ServerReplicaInterface;

/**
 * Classe che implementa le funzionalità del client del filesystem.
 * 
 * Per qualsiasi operazione il client deve contattare il ServerMaster, il quale dirà con quale ServerReplica dovrà 
 * comunicare per completare l'operazione.
 * 
 * Il client quindi comunicherà direttamente con il ServerReplica.
 * 
 * Nella classe è tenuto il riferimento al ServerMaster e a tutti i possibili ServerReplica così non serve fare il lookup
 * ogni volta.
 * 
 * @author gabrielesavoia
 *
 */
public class Client {
	
	private ServerReplicaInterface[] replicas;
	private ServerMasterInterface master;
	
	/**
	 * Costruttore classe.
	 * Faccio lookup del ServerMaster e poi anche di tutti i ServerReplica così non devo fare lookup ogni volta.
	 * 
	 * Se almeno 1 ServerReplica è disponibile procedo con la creazione del cient, altrimenti genero errore.
	 * 
	 * @throws DfsException Generata se non è possibile fare il lookup di tutti i ServerReplica oppure non è
	 * 			possibile fare il lookup del ServerMaster
	 */
	public Client() throws DfsException{
		
		int numReplicas = 0;
		
		try {
			master = (ServerMasterInterface) Naming.lookup (Configs.URL+"ServerMaster");
			numReplicas = master.getNumServerReplica();
		} catch(Exception e) {
			throw new DfsException("Errore: non è possibile fare il lookup (o ottenere il numero di ServerReplica) del ServerMaster", true);
		}
		
		// lookup dei ServerReplica
		int numFailed = 0;
		try {
			replicas = new ServerReplicaInterface[numReplicas];
			for (int i=0; i<numReplicas; i++) {
				replicas[i] = (ServerReplicaInterface) Naming.lookup (Configs.URL+"ReplicaServer"+i);
			}
		} catch(Exception e) { numFailed += 1; }
		
		if (numFailed == numReplicas) {
			throw new DfsException("Errore: non è possibile fare il lookup di nessun ServerReplica", true);
		}
		
	}
	
	/**
	 * Funzione che rende i path uniformi: 
	 * 		- tutti i path iniziano con lo '/';
	 * 		- tutte le directory terminano con '/'.
	 * 
	 * Nel caso un path contenga ../ allora non è accettato in quanto potrebbe essere pericoloso.
	 * 
	 * Riconosco un file da una directory dal path: se c'è il punto è un file (perchè ha l'estensione), altrimenti è
	 * una directory.
	 * 
	 * @param path inserito dall'utente
	 * @return path modificato e reso uniforme
	 */
	private String getCleanedPath(String path) throws DfsException {
		String res = new String(path);            // copia stringa
		
		if ( !res.startsWith("/") ) { res = "/"+res; }
		
		// non si accettano path con ../ al suo interno
		for (int i=0;i<res.length()-1;i++) {
			if ( (res.charAt(i) == '.') && (res.charAt(i+1) == '.') ) {
				throw new DfsException("Errore: path non valido \'../\' non è accettato");
			}
		}
		
		// mi assicuro che le directory terminino con lo slash finale
		if ( !(path.contains(".")) && !(path.endsWith("/")) ) {
			res = res+"/";
		}
	
		return res;
	}
	
	/**
	 * Creazione di un file. Il file deve contenere l'estensione.
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (creazione file). Questo si occuperà di 
	 * 		  propagare la modifica a tutti gli altri ServerReplica;
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path del file da creare
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public void createFile(String path) throws DfsException {
		
		// i file devono avere '.estensione'
		if (!path.contains(".")) { throw new DfsException("Errore: nome del file non valido, deve contenere l'estensione"); }
		
		path = getCleanedPath(path);
		int idReplica = -1;
		
		// start operation
		try { idReplica = master.startOperation('w', path); }
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// creazione file
		try { replicas[idReplica].createFile(path, true); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi di creazione
			try { master.endOperation('w', path); }
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(), ((DfsException)e).needExitProgram());}
		}
		
		
		// end operation se non ci sono stati problemi
		try { master.endOperation('w', path); }
		catch (RemoteException e) {
			throw new DfsException("Errore: problema di connessione con il ServerMaster ", true);
		}
		
	}
	
	/**
	 * Scrittura di un file (ne crea uno nuovo se non esiste). 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (scrittura file). Questo si occuperà di 
	 * 		  propagare la modifica a tutti gli altri ServerReplica;
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path del file da scrivere
	 * @param content da scrivere
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public void writeFile(String path, byte[] content) throws DfsException {
		
		path = getCleanedPath(path);
		
		// i file devono avere '.estensione'
		if (!path.contains(".")) { throw new DfsException("Errore: il nome del file deve contenere l'estensione");  }
		
		int idReplica = -1;
		
		// start operation
		try { idReplica = master.startOperation('w', path); }
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// scrittura file
		try { replicas[idReplica].writeFile(path, content, true); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { master.endOperation('w', path); }
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica ,riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { master.endOperation('w', path); }
		catch (RemoteException e) {
			throw new DfsException("Errore: problema di connessione con il ServerMaster ", true);
		}
		
	}
	
	/**
	 * Lettura di un file. 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (lettura file);
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path del file da leggere
	 * 
	 * @return array di byte che corrispondono al contenuto del file letto
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public byte[] readFile(String path) throws DfsException {
		
		path = getCleanedPath(path);
		
		if (!path.contains(".")) { throw new DfsException("Errore: specificare il nome di un file da leggere");  }
		
		byte[] res = null;
		int idReplica = -1;
		
		// start operation
		try { idReplica = master.startOperation('r', path); }
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// lettura file
		try { res = replicas[idReplica].readFile(path); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { master.endOperation('r', path); }
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { master.endOperation('r', path); }
		catch (RemoteException e) {
			throw new DfsException("Errore: problema di connessione con il ServerMaster ", true);
		}
		
		return res;
		
	}
	
	/**
	 * Rimozione di un file / directory. 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (rimozione file / directory). Questo 
	 * 		  si occuperà di propagare la modifica a tutti gli altri ServerReplica;
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path del file / directory da eliminare
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public void remove(String path) throws DfsException {
		
		path = getCleanedPath(path);
		int idReplica = -1;
		
		// start operation
		try { idReplica = master.startOperation('w', path); }
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// rimozione file/directory
		try { replicas[idReplica].remove(path, true); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { master.endOperation('w', path); }
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { master.endOperation('w', path); }
		catch (RemoteException e) {
			throw new DfsException("Errore: problema di connessione con il ServerMaster ", true);
		}
		
	}
	
	/**
	 * Spostamento di un file. 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (spostamento file). Questo 
	 * 		  si occuperà di propagare la modifica a tutti gli altri ServerReplica;
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param sourcePath path del file da spostare (es. "/dir/file.txt")
	 * @param targetPath path target (es. "/newDir/file.txt")
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public void moveFile(String sourcePath, String targetPath) throws DfsException {
		
		sourcePath = getCleanedPath(sourcePath);
		targetPath = getCleanedPath(targetPath);
		
		if (!sourcePath.contains(".")) { throw new DfsException("Errore: è possibile spostare solo file"); }
		if (!targetPath.contains(".")) { throw new DfsException("Errore: il target path deve contenere l'estensione"); }
		
		if(sourcePath.equals(targetPath)) { throw new DfsException("Errore: il source path è ugule a target path"); }
		
		int idReplica = -1;
		
		// start operation: sia per il source che per il target
		try { 
			idReplica = master.startOperation('w', sourcePath);
			master.startOperation('w', targetPath);
		}
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// spostamento file
		try { replicas[idReplica].moveFile(sourcePath, targetPath, true); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { 
				master.endOperation('w', sourcePath); 
				master.endOperation('w', targetPath); 
			}
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { 
			master.endOperation('w', sourcePath); 
			master.endOperation('w', targetPath); 
		}
		catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
	}
	
	/**
	 * Rinomina di un file. 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (rinomina file). Questo si occuperà di 
	 * 		  propagare la modifica a tutti gli altri ServerReplica;
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path del file da rinominare
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public void renameFile(String path, String newName) throws DfsException {
		
		if ( newName.contains("/") ) { throw new DfsException("Errore: nuovo nome non valido, / è un carattere non ammesso"); }
		if ( !newName.contains(".") ) { throw new DfsException("Errore: nuovo nome non valido, deve avere l'estensione"); }
		
		path = getCleanedPath(path);
		String pathWithNewName = Paths.get(path).resolveSibling(newName).toString();
		int idReplica = -1;
		
		// start operation: sia per il path che per il path con il nuovo nome
		try { 
			idReplica = master.startOperation('w', path);
			master.startOperation('w', pathWithNewName);
		}
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// rinomina file
		try { replicas[idReplica].renameFile(path, newName, true); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { 
				master.endOperation('w', path); 
				master.endOperation('w', pathWithNewName); 
			}
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { 
			master.endOperation('w', path); 
			master.endOperation('w', pathWithNewName); 
		}
		catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
	}	
	
	/**
	 * Creazione directory. 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (creazione directory). Questo si occuperà di 
	 * 		  propagare la modifica a tutti gli altri ServerReplica;
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path path della directory da creare
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public void createDirectory(String path) throws DfsException {
		
		if (path.contains(".")) { throw new DfsException("Errore: nome della directory non valido, non può contenere il punto"); }
		
		path = getCleanedPath(path);
		int idReplica = -1;
		
		// start operation
		try { idReplica = master.startOperation('w', path); }
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// creazione directory
		try { replicas[idReplica].createDirectory(path, true); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { master.endOperation('w', path); }
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { master.endOperation('w', path); }
		catch (RemoteException e) {
			throw new DfsException("Errore: problema di connessione con il ServerMaster ", true);
		}
		
	}
	
	/**
	 * Listaggio contenuto directory. 
	 * 
	 * Passaggi :
	 * 		- contatta il ServerMaster che gestisce i lock e ritorna l'id del ServerReplica;
	 * 		- contatta il ServerReplica con l'id specificato ed esegue l'operazione (creazione directory);
	 * 		- contatta il ServerMaster per dire che l'operazione è terminata;
	 * 
	 * Se si verifica un problema durante lo svolgimento dell' operazione, comunico al ServerMaster che l'operazione 
	 * è terminata.
	 * 
	 * @param path path della directory da listare
	 * 
	 * @return lista di stringhe che corrispondono ai file / directory contenuti nella directory listata
	 * 
	 * @throws DfsException generata nel caso di problemi
	 */
	public String[] listFilesDirectory(String path) throws DfsException {
		
		path = getCleanedPath(path);
		
		if (path.contains(".")) { throw new DfsException("Errore: il listaggio si può effettuare solo su una directory"); }
		
		int idReplica = -1;
		String[] res = null;
		
		// start operation
		try { idReplica = master.startOperation('r', path); }
		catch (RemoteException e) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
		
		// lettura file
		try { res = replicas[idReplica].listFilesDirectory(path); }
		catch (Exception e) {
			
			// end operation se ci sono stati problemi
			try { master.endOperation('r', path); }
			catch (RemoteException exc) { throw new DfsException("Errore: problema connessione con ServerMaster ", true); }
			
			// può essere che il ServerReplica vada in down dopo che ho fatto il startOperation() e quindi lo gestisco 
			// dicendo al client di riprovare
			if(e instanceof RemoteException) { throw new DfsException("Errore: problema connessione con ServerReplica, riprova"); }
			if(e instanceof DfsException) { throw new DfsException(e.getMessage(),((DfsException)e).needExitProgram());}
		}
		
		// end operation se non ci sono stati problemi
		try { master.endOperation('r', path); }
		catch (RemoteException e) {
			throw new DfsException("Errore: problema di connessione con il ServerMaster ", true);
		}
		
		return res;
		
	}

}
