package common;

/**
 * Classe per la gest
 * @author gabrielesavoia
 *
 */
public class DfsException extends Exception{
	
    private static final long serialVersionUID = 1L;
    private boolean needExitProgram;
    
	public DfsException(String errorMessage) {
        super(errorMessage);
        
        this.needExitProgram = false;
    }

	public DfsException(String errorMessage, boolean needExitProgram) {
        super(errorMessage);
        
        this.needExitProgram = needExitProgram;
    }
	
	public boolean needExitProgram() {
		return needExitProgram;
	}

}
