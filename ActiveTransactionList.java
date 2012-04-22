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
//import java.nio.ByteBuffer;
import java.util.HashMap;
public class ActiveTransactionList{

    /*
     * You can alter or add to these suggested methods.
     */
	private HashMap activeTransactions;
    private SimpleLock activeTransactionsMutex;
    Transaction temp = new Transaction();    
    public ActiveTransactionList() {
    	activeTransactions = new HashMap<Long, Transaction>(); 
    	activeTransactionsMutex = new SimpleLock();
      }

    public void put(Transaction trans){
        //System.exit(-1); // TBD
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
        //System.exit(-1); // TBD
        //return null;
    	try {
    		activeTransactionsMutex.lock();
            Transaction temp = (Transaction)activeTransactions.get(tid.getTidfromTransID());
          } 
          finally {
        	  activeTransactionsMutex.unlock();
        	  return temp; 
          }
    }

    public Transaction remove(TransID tid){
        //System.exit(-1); // TBD
        //return null;
    	try {
    		activeTransactionsMutex.lock();
            Transaction temp = (Transaction)activeTransactions.remove(tid.getTidfromTransID());
          } 
          finally {
        	  activeTransactionsMutex.unlock();
        	  return temp; 
          }
    }


}