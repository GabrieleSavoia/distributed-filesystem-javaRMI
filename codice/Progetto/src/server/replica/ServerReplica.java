package server.replica;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import common.Configs;
import common.DfsException;

/**
 * Classe per la gestione di un ServerReplica.
 * Il concetto è che nel sistema ci sono tanti ServerReplica che contengono tutti una copia del filesystem distribuito e che
 * dovranno essere aggiornati nel momento in cui si effettuano delle modifiche.
 * 
 * Quando un client vuole fare un'operazione, contatterà il ServerMaster il quale ritornerà poi l'ID del ServerReplica
 * da contattare.
 * Il client quindi parlerà direttamente con il ServerReplica opportuno.
 * 
 * Nel momento in cui il client vuole effettuare modifiche nel filesystem, sarà necessario propagare le modifiche
 * su tutti i ServerReplica.
 * 
 * @author gabrielesavoia
 *
 */
public class ServerReplica extends UnicastRemoteObject implements ServerReplicaInterface{
	
	private static final long serialVersionUID = 1L;
	
	private String replicaPoint;
	private String replicaId;
	
	private int numReplicas = -1;
	private ServerReplicaInterface[] replicas;

	/**
	 * Costruttore.
	 * Prende in input l'export point, ovvero il path della cartella che diventa il root del 
	 * filesystem distribuito.
	 * 
	 * @param replicaPoint Path del replica point (deve esistere nel server e deve essere una cartella)
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws InvalidPathException Generata se il path dell'export point non esiste oppure non è una directory
	 */
	public ServerReplica(String replicaPoint, String replicaId) throws RemoteException, DfsException {
		super();
		
		if ( !setReplicaPoint(replicaPoint+replicaId) ) {
			throw new DfsException("Errore: non è possibile creare / settare la directory di replica");
		}
		
		this.replicaId = replicaId;
		
	}
	
	/**
	 * Eliminazione totale dei contenuti di una directory e della directory stessa.
	 * 
	 * @param path della directory da eliminare
	 * @throws IOException
	 */
	private void deleteDirectoryAndContent(Path path) throws IOException {
		
		if (!Files.exists(path)) { return; }
		
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
				for (Path entry : entries) {
					deleteDirectoryAndContent(entry);
				}
			}
		}
		Files.delete(path);
	}
	
	/**
	 * Creo la directory di replica vuota (se esisteva già elimino tutti i suoi contenuti).
	 * 
	 * Setto la directory come replicaPoint ed elimino lo slash finale se presente.
	 * 
	 * @param replicaPoint Punto in cui avviene la replicazione dei dati
	 * @return True se tutto è ok, False altrimenti
	 */
	private boolean setReplicaPoint(String replicaPoint) {
		
		if (replicaPoint == null) { return false; }
		
		// se esiste elimino directory e il suo contenuto
		try { deleteDirectoryAndContent(Paths.get(replicaPoint)); }
		catch(Exception e) { return false; }
		
		// creo directory vuota (creo anche le directory padri se non esistono)
		try { Files.createDirectories(Paths.get(replicaPoint)); }
		catch(Exception e) { return false; }
		
		// elimino slash finale se presente
		if ( replicaPoint.endsWith("/") ) { this.replicaPoint = replicaPoint.substring(0, replicaPoint.length() - 1); }
		else { this.replicaPoint = replicaPoint; }
		
		return true;
	}

	/**
	 * Creazione di un nuovo file che si trova in 'path'.
	 * 
	 * @param path Percorso del file da creare. Es: '/ExistingFolder/newFileName.txt'
	 * @param needPropagate true se è necessario propagare l'operazione tra i ServerReplica, false altrimenti
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void createFile(String path, boolean needPropagate) throws RemoteException, DfsException{
		
		try{
			Files.createFile(Paths.get(replicaPoint+path));
		}catch(FileAlreadyExistsException e) {
			throw new DfsException("Errore: il file esiste già");
		}catch(IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}
		
		if (needPropagate) { propagate("createFile", path, null, null); }
		
	}

	/**
	 * Scrittura di un array di byte su un file.
	 * Creo il file nel caso questo non esista.
	 * 
	 * @param path Percorso del file da scrivere
	 * @param content array di byte che rappresentano il contenuto del file
	 * @param needPropagate true se è necessario propagare l'operazione tra i ServerReplica, false altrimenti
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void writeFile(String path, byte[] content, boolean needPropagate) throws RemoteException, DfsException{
		
		try{
			Files.write(Paths.get(replicaPoint+path), content, StandardOpenOption.CREATE);
		}catch(IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}
		
		if (needPropagate) { propagate("writeFile", path, null, content); }
		
	}
	
	/**
	 * Lettura file (non deve essere troppo grande se no non sta in memoria).
	 * 
	 * @param path Percorso del file dal leggere
	 * @return array di byte
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public byte[] readFile(String path) throws RemoteException, DfsException {
		
		try {
			return Files.readAllBytes(Paths.get(replicaPoint+path));
		}catch(IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}
		
	}

	/**
	 * Rimuove un file oppure una directory specificata da 'path'.
	 * Nel caso in cui una directory ha elementi al suo interno allora non è eliminabile.
	 * 
	 * @param path Percorso del file (o directory) da eliminare
	 * @param needPropagate true se è necessario propagare l'operazione tra i ServerReplica, false altrimenti
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void remove(String path, boolean needPropagate) throws RemoteException, DfsException{
		
		try{
			Files.delete(Paths.get(replicaPoint+path));
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}catch(NoSuchFileException e) {
			throw new DfsException("Errore: il file non esiste");
		}catch(DirectoryNotEmptyException e) {
			throw new DfsException("Errore: la directory non è vuota");
		}catch (IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}
		
		if (needPropagate) { propagate("remove", path, null, null); }
	
	}

	/**
	 * Muovo il file (e non directory) dal 'sourcePath' al 'targetPath'.
	 * Se il 'targetPath' esiste già, allora non è possibile effettuare lo spostamento.
	 * 
	 * @param sourcePath Percorso del file da muovere (es. /folder/file.txt )
	 * @param targetPath Percorso in cui voglio muovere il file (es. /otherFolder/file.txt)
	 * @param needPropagate true se è necessario propagare l'operazione tra i ServerReplica, false altrimenti
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void moveFile(String sourcePath, String targetPath, boolean needPropagate) throws RemoteException, DfsException{
		
		// posso spostare solo file non directory
		if ( Files.isDirectory(Paths.get(replicaPoint+targetPath)) ) {
			throw new DfsException("Errore: non è possibile spostare directory");
		}
		
		if ( Files.exists(Paths.get(replicaPoint+targetPath)) ) {
			throw new DfsException("Errore: nella nuova posizione esiste un file con lo stesso nome");
		}
		
		try {
			Files.move( Paths.get(replicaPoint+sourcePath), 
					Paths.get(replicaPoint+targetPath), 
					StandardCopyOption.ATOMIC_MOVE);
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}catch (IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}
		
		if (needPropagate) { propagate("moveFile", sourcePath, targetPath, null); }
	}

	/**
	 * Rinomina di un file.
	 * 
	 * @param path Percorso del file da rinominare
	 * @param newName Nuovo nome
	 * @param needPropagate true se è necessario propagare l'operazione tra i ServerReplica, false altrimenti
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void renameFile(String path, String newName, boolean needPropagate) throws RemoteException, DfsException {
		
		// posso spostare solo file non directory
		if ( Files.isDirectory(Paths.get(replicaPoint+path)) ) {
			throw new DfsException("Errore: non è possibile rinominare directory");
		}
		
		Path source = Paths.get(this.replicaPoint+path);
		Path target = source.resolveSibling(newName);
		
		if ( Files.exists(target) ) { 
			throw new DfsException("Errore: un file esiste già con questo nome");
		}
		
		// i file devono avere '.estensione'
		if (!target.toString().contains(".")) { 
			throw new DfsException("Errore: nuovo nome del file non valido, deve contenere l'estensione"); 
		}
		
		try {
			Files.move(source, source.resolveSibling(newName), StandardCopyOption.ATOMIC_MOVE);
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}catch (IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}
		
		if (needPropagate) { propagate("renameFile", path, newName, null); }
		
	}

	/**
	 * Creazione di una directory.
	 * 
	 * @param path Posizione della directory da creare
	 * @param needPropagate true se è necessario propagare l'operazione tra i ServerReplica, false altrimenti
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void createDirectory(String path, boolean needPropagate) throws RemoteException, DfsException  {
		
		try {
			Files.createDirectory(Paths.get(replicaPoint+path)); // createDirectories: crea quelle che non esistono
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}catch (FileAlreadyExistsException e) {
			throw new DfsException("Errore: la directory esiste già");
		}catch (IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}
		
		if (needPropagate) { propagate("createDirectory", path, null, null); }
		
	}

	/**
	 * Ritorno i file (e directory) contenuti nella directory specificata.
	 * 
	 * @param path Posizione della directory di cui listare i file (e directory)
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public String[] listFilesDirectory(String directoryPath) throws RemoteException, DfsException {
		
		if ( !Files.exists(Paths.get(this.replicaPoint+directoryPath)) ) { 
			throw new DfsException("Errore: la directory non esiste");
		}
		if ( !Files.isDirectory(Paths.get(this.replicaPoint+directoryPath)) ) { 
			throw new DfsException("Errore: non è una directory"); 
		}
		
		try {
			return Files.list(Paths.get(this.replicaPoint+directoryPath))
					.map(Path::getFileName)
					.map(Path::toString)
					.toArray(String[]::new);
		}catch(SecurityException e) {
			throw new DfsException("Errore: violazione sicurezza");
		}catch (IOException e) {
			throw new DfsException("Errore: problema di I/O");
		}
	}
	
	/**
	 * Funzione che esegue il lookup di tutte le altre repliche e salva il relativo oggetto remoto in una lista che sarà 
	 * poi usata durante la propagazione (così nella propagazione non devo rifare il lookup).
	 * 
	 * Questa funzione è chiamata nel costruttore del ServerMaster per ogni ServerReplica. In questo modo quando il 
	 * ServerMaster parte sarà responsabile del configurare tutti i ServerReplica.
	 * 
	 * Perchè non chiamarla nel costruttore del ServerReplica? perchè non è detto che tutte le altre repliche siano attive
	 * 
	 * @param numReplicas Numero di ServerReplica nel sistema
	 * 
	 * @throws RemoteException Generata se ci sono problemi di connessione
	 * @throws DfsException Generata per qualsiasi altro problema
	 */
	@Override
	public void lookupOtherReplicas(int numReplicas) throws RemoteException, DfsException{
		
		this.numReplicas = numReplicas;

		replicas = new ServerReplicaInterface[this.numReplicas];
		
		for (int i=0; i<this.numReplicas; i++) {
			
			if (i == Integer.parseInt(replicaId) ) { continue; }
			
			try {
				replicas[i] = (ServerReplicaInterface) Naming.lookup (Configs.URL+"ReplicaServer"+i);
			}
			catch(Exception e) {
				throw new DfsException("Errore: connessione rifiutata durante la ricerca del ServerReplica "+i, true);
			}
			
		}
		
		System.out.println("Lookups delle altre repliche avvenuti con successo");
		
	}
	
	/**
	 * Propagazione dell'operazione a tutti gli altri ServerReplica.
	 * 
	 * Nel caso in cui un ServerReplica non sia raggiungibile (RemoteException: è andato in down) allora continuo la 
	 * propagazione senza dare errori.
	 * 
	 * Se invece è generata un'eccezione diversa da RemoteException allora non continuo la progpagazione perchè
	 * significa che qualche replica non è consistente (es. mancano o ci sono file / directory diverse tra i ServerReplica).
	 * 
	 * @param opType tipo di operazione da propagare
	 * @param path su cui fare l'operazione
	 * @param otherPath alcune operazioni hanno bisogno di questo parametro aggiuntivo, per le altre lo definisco null
	 * @param content parametro che contiene il content del file per l'operazione di scrittura, è null per tutte le altre operazioni
	 * 
	 * @throws DfsException Generata per qualsiasi problema di propagazione
	 */
	private void propagate(String opType, String path, String otherPath, byte[] content) throws DfsException{
		
		if (replicas == null) { throw new DfsException("Errore: propagazione non avvenuta perchè questa replica non è connessa con le altre"); }
		
		if (numReplicas <= 0) { throw new DfsException("Errore: propagazione non avvenuta perchè il numero di ServerReplica del sistema risulta 0", true); }
		
		for (int i=0; i<numReplicas; i++) {
			
			if (i == Integer.parseInt(replicaId) ) { continue; }
			
			try {
				
				switch(opType) {
					case "createFile": 
						replicas[i].createFile(path, false);
						break;
					case "writeFile":
						replicas[i].writeFile(path, content, false);
						break;
					case "remove":
						replicas[i].remove(path, false);
						break;
					case "moveFile":
						replicas[i].moveFile(path, otherPath, false);
						break;
					case "renameFile":
						replicas[i].renameFile(path, otherPath, false);
						break;
					case "createDirectory":
						replicas[i].createDirectory(path, false);
						break;
					default:
						throw new DfsException("Errore: propagazione non avvenuta perchè l'operazione non è valida", true);
				}
				
			}
			catch(Exception e) {
				// se RemoteException --> continuo il ciclo di propagazione
				// altrimenti --> genero eccezione perchè le repliche non sono consistenti
				if( !(e instanceof RemoteException) ) { 
					throw new DfsException("Errore: ServerReplica "+i+" è raggiungibile ma non è consistente", true);
				}
			}
		}
	}
	
	/**
	 * Funzione che ritorna true se il ServerReplica è raggiungibile.
	 * 
	 * @return true se il ServerReplica è raggiungibile
	 * @throws RemoteException se non è raggiungibile
	 */
	@Override
	public boolean isAlive()throws RemoteException {
		return true;
	}
}
