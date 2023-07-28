package client;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import common.DfsException;

/**
 * Comandi per l'esecuzione:	
 * 		$ cd ~/eclipse-workspace/Progetto/bin	
 * 		$ java client.ClientRun
 * 
 * IMPORTANTE: è necessario che il registry sia attivo. Se non è attivo esegui:
 * 		$ cd ~/eclipse-workspace/Progetto/bin
 * 		$ rmiregistry
 * 
 * Si occupa di far eseguire il client del filesystem distribuito. 
 * Si tratta di una simulazione di un normale terminale in cui l'utente può eseguire determinati comandi per interagire
 * con il filsystem distribuito.
 * 
 * E' necessario che il ServerMaster e tutti i ServerReplica siano attivi.
 * 
 * @author gabrielesavoia
 *
 */
public class ClientRun {

	public static void main(String[] args) {

		Client client = null;
		try {
			client = new Client();
			System.out.println("Lookup del ServerMaster e dei ServerReplica avvenuto con successo");
		} catch (DfsException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		
		Scanner scanner = new Scanner(System.in);
		
		String commandHelp = "\n\t$create [path] : crea un nuovo file. Il 'path' contiene anche il nome del file;"+
						     "\n\t$write [path] [content] : scrive il contenuto 'content' nel file localizzato in 'path';"+
						     "\n\t$read [path] : lettura del file localizzato in 'path';"+
						     "\n\t$rm [path] : rimozione file (o directory) localizzato in 'path';"+
						     "\n\t$move [sourcePath] [targetPath] : sposto file da 'source' a 'target' path;"+
						     "\n\t$rename [path] [newName] rinomina file localizzato in 'path';"+
						     "\n\t$mkdir [path] : creazione directory localizzata in 'path';"+
						     "\n\t$ls [path] : visualizzazione dei contenuti della directory localizzata in 'path';"+
						     "\n\t$help : visualizzazione di tutti i possibili comandi;"+
						     "\n";
		System.out.println("Di seguito la lista dei possibili comandi:\n"+commandHelp);
		
		while( scanner.hasNextLine() ) {
			
			String[] scannerInput = scanner.nextLine().split(" ");
			
			if ("".equals(scannerInput[0])) { continue; }   			// utente andato a capo senza comandi
			if ("exit".equalsIgnoreCase(scannerInput[0])) { break; }
			
			String path = "";
			
			switch (scannerInput[0]){
				case "create":
					
					if (scannerInput.length >= 2) {
						path = scannerInput[1];
					}else {
						System.out.println("\n--> $create [path] : richiesto argomento 'path' \n");
						continue;
					}
	
					try {
						client.createFile(path);
						System.out.println("\n--> Creazione file avvenuta correttamente\n");
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
					
					break;
					
				case "write": 
					
					StringBuilder content = new StringBuilder("");
					if (scannerInput.length >= 3) {
						path = scannerInput[1];
						for (int i=2; i<scannerInput.length; i++) {
							content.append(scannerInput[i]+" ");
						}
					}else {
						System.out.println("\n--> $write [path] [content] : richiesti argomenti 'path' e 'content'\n");
						continue;
					}
					
					try {
						client.writeFile(path, content.toString().getBytes());
						System.out.println("\n--> File scritto correttamente\n");
					}  catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;
							
				case "read": 
					
					if (scannerInput.length >= 2) {
						path = scannerInput[1];
					}else {
						System.out.println("\n--> $read [path] : richiesto argomento 'path' \n");
						continue;
					}
					
					try {
						
						String file = new String(client.readFile(path), StandardCharsets.UTF_8);
						System.out.println("\n--> Contenuto del file "+path+":\n\n");
						System.out.println(file+"\n\n");
						
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;
							
				case "rm": 
					
					if (scannerInput.length >= 2) {
						path = scannerInput[1];
					}else {
						System.out.println("\n--> $rm [path] : richiesto argomento 'path' \n");
						continue;
					}
					
					try {
						client.remove(path);
						System.out.println("\n--> Rimozione file (o directory) avvenuta correttamente\n");
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;
					
				case "move": 
					
					String target = "";
					if (scannerInput.length >= 3) {
						path = scannerInput[1];
						target = scannerInput[2];
					}else {
						System.out.println("\n--> $move [sourcePath] [targetPath] : richiesti argomenti 'sourcePath' e 'targetPath' \n");
						continue;
					}
					
					try {
						client.moveFile(path, target);
						System.out.println("\n--> Spostamento file avvenuto correttamente\n");
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;	
					
				case "rename": 
					
					String newName = "";
					if (scannerInput.length >= 3) {
						path = scannerInput[1];
						newName = scannerInput[2];
					}else {
						System.out.println("\n--> $rename [path] [newName] : richiesti argomenti 'path' e 'newName' \n");
						continue;
					}
					
					try {
						client.renameFile(path, newName);
						System.out.println("\n--> Rinomina file avvenuta correttamente\n");
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;
					
				case "mkdir": 
					
					if (scannerInput.length >= 2) {
						path = scannerInput[1];
					}else {
						System.out.println("\n--> $mkdir [path] : richiesto argomento 'path'\n");
						continue;
					}
					
					try {
						client.createDirectory(path);
						System.out.println("\n--> Directory creata correttamente\n");
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;
					
				case "ls": 
					
					if (scannerInput.length >= 2) {
						path = scannerInput[1];
					}else {
						System.out.println("\n--> $ls [path] : richiesto argomento 'path'\n");
						continue;
					}
					
					try {
						String[] files = client.listFilesDirectory(path);
						StringBuilder filesString = new StringBuilder("\n--> ");
						for (String file: files) {
							filesString.append(file+"    ");
						}
						System.out.println(filesString+"\n");
					} catch (DfsException e) {
						System.out.println("\n--> "+e.getMessage()+"\n");
						if (e.needExitProgram()) { System.exit(1); }
					}
			
					break;
					
				case "help": 
					
					System.out.println(commandHelp);
					break;
			
				default: System.out.println("\n-->comando non riconosciuto\n");
			}
			
		}
		
		scanner.close();
		System.out.println("Client terminato");
		
	}	
		
}

