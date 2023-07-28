package server.master;

import java.util.HashMap;

/**
 * Classe per la gestione dei lock e dei reader.
 * 
 * Si occupa di mantenere una hashmap in cui le chiavi sono i path su cui avvengono le operazioni e 
 * come valori ci sono istanze di CustomLock che tengono traccia dei write lock e dei reader in relazione ad 
 * un certo path.
 * 
 * @author gabrielesavoia
 *
 */
public class LockManager {
	
	/**
	 * Classe che tiene traccia dei write lock e dei reader in relazione al path specificato nella hashtable.
	 * 
	 * @author gabrielesavoia
	 *
	 */
	private class CustomLock {
		
		private int readers;
		private boolean writeLock;
		
		/**
		 * Costruttore che mette a zero i reader e non mette alcun write lock.
		 */
		public CustomLock() {
			this.readers = 0;
			this.writeLock = false;
		}
		
		/**
		 * Controlla se qualcuno sta leggendo.
		 * 
		 * @return true se qualcuno sta leggendo, false altrimenti
		 */
		public boolean isSomeoneReading() {
			return (readers > 0) ? true: false;
		}
		
		/**
		 * Aggiunge un reader.
		 */
		public void addReader() {
			readers += 1;
		}
		
		/**
		 * Elimina un reader.
		 */
		public void delReader() {
			if (readers > 0) { readers -= 1; }
		}
		
		/**
		 * Controlla se c'è un write lock.
		 * 
		 * @return true se c'è un write lock, false altrimenti
		 */
		public boolean isWriteLocked() {
			return writeLock;
		}
		
		/**
		 * Settaggio del write lock.
		 * 
		 * @param writeLock true se si vuole settare il write lock, false se si vuole togliere il write lock
		 */
		public void setWriteLock(boolean writeLock) {
			this.writeLock = writeLock;
		}
		
	}
	
	public HashMap<String, CustomLock> lockMap;
	
	/**
	 * Costruttore classe in cui creo una hashmap vuota.
	 */
	public LockManager() {
		
		lockMap = new HashMap<String, CustomLock>(); 
		
	}
	
	/**
	 * Settaggio write lock in riferimento ad un certo path.
	 * 
	 * Se il path non esiste nella mappa, lo aggiungo.
	 * 
	 * @param path del file su cui fare write lock
	 * 
	 * @return true se è possibile settare il write lock, false se non è possibile (ad esempio se un altro thread aveva già
	 * 			il write lock oppure se qualcuno era già in lettura)
	 */
	public synchronized boolean writeLock(String path) {
		
		CustomLock lock = lockMap.get(path);
		
		if (lock == null) {
			lock = new CustomLock();
			lockMap.put( path, lock );
		}
		
		if ( (lock.isWriteLocked()) || (lock.isSomeoneReading()) ) { 
			return false;
		}
		
		lock.setWriteLock(true);
		return true;
		
	}
	
	/**
	 * Tolgo il write lock in riferimento ad un certo path.
	 * 
	 * Una volta tolto il write lock, se non è utilizzata, viene tolta la riga del path dalla hashtable.
	 * 
	 * @param path del file di cui fare write unlock
	 * 
	 * @return true se è possibile togliere il write lock, false se non è possibile (ad esempio se nella hashmap non era
	 * 			presente il path come chiave)
	 */
	public synchronized boolean writeUnlock(String path) {
		
		CustomLock lock = lockMap.get(path);
		if (lock == null) {
			return false;
		}
		
		lock.setWriteLock(false);
		deleteIfNotUsed(path, lock);
		return true;
		
	}
	
	/**
	 * Elimino dalla hashtable dei lock il path nel caso in cui nessuno lo stia usando (ovvero se non è write locked 
	 * e se nessuno sta leggendo).
	 * 
	 * @param path del file da controllare
	 * @param lock relativo al path
	 */
	private synchronized void deleteIfNotUsed(String path, CustomLock lock) {
		
		if ( !(lock.isWriteLocked()) && !(lock.isSomeoneReading()) ) { 
			lockMap.remove(path);
		}
		
	}
	
	/**
	 * Aggiungo reader in riferimento ad un certo path.

	 * Se il path non esiste nella hashmap, lo aggiungo.
	 * 
	 * @param path del file a cui aggiungere il reader
	 * 
	 * @return true se è possibile aggiungere il reader, false se non è possibile (ad esempio se qualcuno ha messo ed è 
	 * 			ancora attivo il write lock)
	 */
	public synchronized boolean addReader(String path) {
		
		CustomLock lock = lockMap.get(path);
		
		if (lock == null) {
			lock = new CustomLock();
			lockMap.put( path, lock );
		}
		
		if (lock.isWriteLocked()) { 
			return false;
		}
		
		lock.addReader();
		return true;
		
	}
	
	/**
	 * Elimino reader in riferimento ad un certo path.
	 * 
	 * Read unlock di un file identificato dal proprio path.
	 * 
	 * Una volta tolto il reader, se non è utilizzata, viene tolta la riga del path dalla hashtable.
	 * 
	 * @param path del file di cui fare l'eliminazione del reader
	 * 
	 * @return true se è possibile togliere il reader, false se non è possibile (ad esempio se nella hashmap non era
	 * 			presente il path come chiave)
	 */
	public synchronized boolean delReader(String path) {
		
		CustomLock lock = lockMap.get(path);
		if (lock == null) {
			return false;
		}
		
		lock.delReader();
		deleteIfNotUsed(path, lock);
		return true;
		
	}
	
	/**
	 * @return Hashmap in stringa.
	 */
	@Override
	public String toString() {
		
		String res = "\n\tHASHMAP CONCURRENCY:\n";
		
		for (String path: lockMap.keySet()) {
			res += "\n"+path+"\t\t writeLock: "+lockMap.get(path).writeLock+"   -   numReaders: "+lockMap.get(path).readers;
		}
		
		return res;
		
	}

}
