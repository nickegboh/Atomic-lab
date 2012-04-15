/*
 * ActiveTransaction.java
 *
 * List of active transactions.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
import java.util.HashMap;
public class ActiveTransactionList{
			
    private Hashmap activeTransactions;
    private SimpleLock activeTransactionsMutex;
        
    public ActiveTransactionList() {
    	writes = new HashMap<Long, Transaction>(); 
    	activeTransactionsMutex = new SimpleLock();
      }
    
    public void put(Transaction trans){
    	try {
    		activeTransactionsMutex.lock();
            Long TID = trans.getTid();
            activeTransactions.put(TID, trans);
          } 
          finally {
        	  activeTransactionsMutex.unlock();
          }
    }

    public Transaction get(TransID tid){
    	try {
    		activeTransactionsMutex.lock();
            Transaction temp = activeTransactions.get(tid.getTidfromTransID());
          } 
          finally {
        	  activeTransactionsMutex.unlock();
        	  return temp; 
          }
    }

    public Transaction remove(TransID tid){
    	try {
    		activeTransactionsMutex.lock();
            Transaction temp = activeTransactions.remove(tid.getTidfromTransID());
          } 
          finally {
        	  activeTransactionsMutex.unlock();
        	  return temp; 
          }
    }


}