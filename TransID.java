/*
 * TransId.java
 *
 * Interface to ADisk
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */
public class TransID{
	
    private static int tid = 0;
    private SimpleLock TransIDLock = new SimpleLock();
    
    public TransID ( tid = 0; ) {}

    public int newTID(){
                try{
                        TransIDLock.lock();
                        return last_tid++;
                } finally {
                        TransIDLock.unlock();
                }
        }
        
        
        
        
        
        
        
        
        
        
        
        
        
