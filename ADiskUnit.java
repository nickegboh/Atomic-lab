import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class ADiskUnit {
	
	  private static ADisk disk;
	  	
	  public static void main(String args[]) throws IllegalArgumentException, IOException
	  {
		disk = new ADisk(true);
		int passcount = 0; 
		int failcount = 0; 
		
		//Test One Write, One Transaction
		if(testonewriteTrans())
			passcount++;
		else 
			failcount++;
		
		//Test Multi Write, One Transaction
		if(testmultiwriteTrans())
			passcount++;
		else 
			failcount++;
		
		//Test Multi Write, One Transaction Commit Test
		if(testmultiwriteTransCommit())
			passcount++;
		else 
			failcount++;
		
		//Test Initial Read
	    if(testInitialRead())
		  passcount++;
		else 
		  failcount++;
	    
	    //Test Trans ID
		if(testTransID())
		  passcount++;
		else 
		  failcount++;
		
	    //Test Parse Log
		if(testParseLog())
		  passcount++;
		else 
		  failcount++;
		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
	  }
	  
	  public static boolean testonewriteTrans() throws IllegalArgumentException, IOException {
		  boolean pass = false; 
		  Transaction temp = new Transaction(disk);
		  int tid = temp.getTid().getTidfromTransID();
		  byte[] dat = new byte[Disk.SECTOR_SIZE];
		  byte[] dat2 = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat.length; i++)
			  dat[i] = 'a';
		  temp.addWrite(5, dat);
		  if(!temp.checkRead(5, dat2))
			  System.out.println("Test One Write, One Trans: check read failed!");
		  if(SectorCheck(dat, dat2))
			  pass = true;
		  if(!pass)
			  return pass; 
		  temp.commit();
		  
		  try {
		  temp.addWrite(7, dat);
		  }
		  catch (IllegalArgumentException e){
			  pass = true; 
		  }

		  if(temp.getNUpdatedSectors() != 1){
			  System.out.println("Test One Write, One Trans: get n updated sectors returned wrong value");
			  pass = false;
		  }
		  if(pass)
			  System.out.println("Test One Write, One Trans: Passed!");
		  else
			  System.out.println("Test One Write, One Trans: Failed!");
		  return pass; 
	  }
	  
	  public static boolean testmultiwriteTrans() throws IllegalArgumentException, IOException {
		  boolean pass = true; 
		  Transaction temp = new Transaction(disk);
		  int tid = temp.getTid().getTidfromTransID();
		  
		  //create samlple datas
		  byte[] dat1 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat2 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat3 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat4 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat5 = new byte[Disk.SECTOR_SIZE];
		  byte[] datret = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat1.length; i++)
			  dat1[i] = 'a';
		  for(int i = 0; i < dat2.length; i++)
			  dat2[i] = 'b';
		  for(int i = 0; i < dat3.length; i++)
			  dat3[i] = 'c';
		  for(int i = 0; i < dat4.length; i++)
			  dat4[i] = 'd';
		  for(int i = 0; i < dat5.length; i++)
			  dat5[i] = 'e';
		  
		  //issue writes
		  temp.addWrite(5, dat1);
		  temp.addWrite(7, dat2);
		  temp.addWrite(9, dat3);
		  temp.addWrite(11, dat4);
		  temp.addWrite(13, dat5);
		  temp.addWrite(15, dat5);
		  temp.addWrite(17, dat5);
		  temp.addWrite(11, dat4);
		  temp.addWrite(11, dat4);
		  temp.addWrite(11, dat1);
		  temp.addWrite(7, dat5);
		  temp.addWrite(7, dat4);
		  temp.addWrite(15, dat4);
		  temp.addWrite(17, dat2);
		  
		  //ensure written sectors have writes logged
		  if(!temp.checkRead(5, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat1, datret))
			  pass = false;
		  if(!temp.checkRead(7, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat4, datret))
			  pass = false;
		  if(!temp.checkRead(9, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat3, datret))
			  pass = false;
		  if(!temp.checkRead(11, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat1, datret))
			  pass = false;
		  if(!temp.checkRead(13, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat5, datret))
			  pass = false;
		  if(!temp.checkRead(15, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat4, datret))
			  pass = false;
		  if(!temp.checkRead(17, datret)){
			  System.out.println("Test Multi Write, One Trans: check read failed!");
			  pass = false; 
		  }
		  if(!SectorCheck(dat2, datret))
			  pass = false;
		  
		  //Commit and Test
		  temp.commit();
		  if(temp.getNUpdatedSectors() != 7){
			  System.out.println("Test Multi Write, One Trans: get n updated sectors returned wrong value");
			  pass = false;
		  }
		  if(pass)
			  System.out.println("Test Multi Write, One Trans: Passed!");
		  else
			  System.out.println("Test Multi Write, One Trans: Failed!");
		  return pass; 
	  }

	  public static boolean testmultiwriteTransCommit() throws IllegalArgumentException, IOException {
		  boolean pass = true; 
		  Map<Integer, byte[]> writes = new HashMap<Integer, byte[]>(); 
		  Transaction temp = new Transaction(disk);
		  int tid = temp.getTid().getTidfromTransID();
		  
		  //create samlple data
		  byte[] dat1 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat2 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat3 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat4 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat5 = new byte[Disk.SECTOR_SIZE];
		  byte[] datret = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat1.length; i++)
			  dat1[i] = 'a';
		  for(int i = 0; i < dat2.length; i++)
			  dat2[i] = 'b';
		  for(int i = 0; i < dat3.length; i++)
			  dat3[i] = 'c';
		  for(int i = 0; i < dat4.length; i++)
			  dat4[i] = 'd';
		  for(int i = 0; i < dat5.length; i++)
			  dat5[i] = 'e';
		  
		  //issue writes
		  temp.addWrite(5, dat1);
		  writes.put(5, dat1);
		  temp.addWrite(7, dat2);
		  writes.put(7, dat2);
		  temp.addWrite(9, dat3);
		  writes.put(9, dat3);
		  temp.addWrite(11, dat4);
		  writes.put(11, dat4);
		  temp.addWrite(13, dat5);
		  writes.put(13, dat5);
		  temp.addWrite(15, dat5);
		  writes.put(15, dat5);
		  temp.addWrite(17, dat5);
		  writes.put(17, dat5);
		  temp.addWrite(11, dat4);
		  writes.put(11, dat4);
		  temp.addWrite(11, dat4);
		  writes.put(11, dat4);
		  temp.addWrite(11, dat1);
		  writes.put(11, dat1);
		  temp.addWrite(7, dat5);
		  writes.put(7, dat5);
		  temp.addWrite(7, dat4);
		  writes.put(7, dat4);
		  temp.addWrite(15, dat4);
		  writes.put(15, dat4);
		  temp.addWrite(17, dat2);
		  writes.put(17, dat2);
		  
		  //Commit and Test
		  temp.commit();
		  int writecount = temp.getNUpdatedSectors();
		  if(writecount != writes.size()){
			  System.out.println("Test Multi Write, One Transaction Commit: get n updated sectors returned wrong value");
			  pass = false;
		  }
		  for(int i = 0; i < writecount; i++){
			  int tempkey = temp.getUpdateI(i, datret);
			  if(!SectorCheck(datret, writes.get(tempkey))){
				  System.out.println("Test Multi Write, One Transaction Commit: check read failed!");
				  pass = false; 
			  }
		  }
		  if(pass)
			  System.out.println("Test Multi Write, One Transaction Commit: Passed!");
		  else
			  System.out.println("Test Multi Write, One Transaction Commit: Failed!");
		  return pass; 
	  }
	  
	  public static boolean testInitialRead(){     
	      try{
	              ADisk tester = new ADisk(true);
	              tester.setFailureProb(0.0f);
	              TransID t1 =  tester.beginTransaction();
	              byte[] zeros = new byte[Disk.SECTOR_SIZE];
	              tester.readSector(t1, ADisk.REDO_LOG_SECTORS, zeros);
	              boolean testGood = true;
	              for(byte x: zeros)
	              {
	                      if(x != 0)
	                    	  testGood = false;
	              }
	              if(testGood){
	                    System.out.println("Test InitialRead: Passed!");
	              		return true;
	              }
	              else{
	                      System.out.println("Test InitialRead: Fails!");
	                      return false;
	              }
	              	
	      }
	      catch(Exception e){
	    	  System.out.println("TestInitialRead Fails");
	    	  return false;
	      }
	  }

	  public static boolean testTransID(){
			boolean testsGood = true;
			TransID t1 = new TransID();
			TransID t2 = new TransID();
			if(t1.getTidfromTransID()>=t2.getTidfromTransID())
				testsGood = false;            
			if(testsGood){
	            System.out.println("Test TransID: Passed!");
				return true; 
			}
			else{
	              System.out.println("Test TransID: Fails!");
	              return false;
			}
		}
	  
	  public static boolean testParseLog() throws IllegalArgumentException, IOException {
		  boolean pass = true; 
		  Transaction temp = new Transaction(disk);
		  int tid = temp.getTid().getTidfromTransID();
		  
		  //create samlple data
		  byte[] dat1 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat2 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat3 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat4 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat5 = new byte[Disk.SECTOR_SIZE];
		  byte[] datret = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat1.length; i++)
			  dat1[i] = 'a';
		  for(int i = 0; i < dat2.length; i++)
			  dat2[i] = 'b';
		  for(int i = 0; i < dat3.length; i++)
			  dat3[i] = 'c';
		  for(int i = 0; i < dat4.length; i++)
			  dat4[i] = 'd';
		  for(int i = 0; i < dat5.length; i++)
			  dat5[i] = 'e';
		  
		  //issue writes
		  temp.addWrite(5, dat1);
		  temp.addWrite(7, dat2);
		  temp.addWrite(9, dat3);
		  temp.addWrite(11, dat4);
		  temp.addWrite(13, dat5);
		  temp.addWrite(15, dat5);
		  temp.addWrite(17, dat5);
		  temp.addWrite(11, dat4);
		  temp.addWrite(11, dat4);
		  temp.addWrite(11, dat1);
		  temp.addWrite(7, dat5);
		  temp.addWrite(7, dat4);
		  temp.addWrite(15, dat4);
		  temp.addWrite(17, dat2);
		  
		  //Commit and Test
		  temp.commit();
		  
		  byte[] logsectors = temp.getSectorsForLogDebug();
		  int sectorCount = temp.parseHeader(Arrays.copyOfRange(logsectors, 0, Disk.SECTOR_SIZE));
		  if(sectorCount != (logsectors.length / Disk.SECTOR_SIZE)){
			  	System.out.println("Test Parse Log: parse header returned wrong size");
			  	pass = false; 
		  }
		  
		  Transaction fromLog = Transaction.parseLogBytesDebug(logsectors, disk);
		  
		  //compare number of updated sectors
		  if(temp.getNUpdatedSectors() != fromLog.getNUpdatedSectors()){
			  	System.out.println("Test Parse Log: Different Number Updated Sectors");
			  	pass = false;
		  }
		  //compate updates
		  else {
			  byte[] fromlogsector = new byte[Disk.SECTOR_SIZE];
			  byte[] tempsector = new byte[Disk.SECTOR_SIZE];
			  for(int i = 0; i < fromLog.getNUpdatedSectors(); i++){
				  int fromlogsecnum = fromLog.getUpdateI(i, fromlogsector);
				  int tempsecnum = temp.getUpdateI(i, tempsector);
				  if(fromlogsecnum != tempsecnum){
					  System.out.println("Test Parse Log: Sector Number Mismatch");
					  pass = false; 
				  }
				  if(!SectorCheck(fromlogsector, tempsector)){
					  System.out.println("Test Parse Log: Sector Data Mismatch");
					  pass = false;   
					  break;
				  }
			  }
		  }
		  
		  
		  
		  //compare logs
		  if(!LogCompare(fromLog.getSectorsForLogDebug(), logsectors)){
			  	System.out.println("Test Parse Log: Log Mismatch");
			  	pass = false; 
		  }
			 
		  if(pass)
			  System.out.println("Test Parse Log: Passed!");
		  else
			  System.out.println("Test Parse Log: Failed!");
		  return pass; 
	  }

	  
	  private static boolean SectorCheck(byte[] dat1, byte[] dat2){
		  if(dat1.length != Disk.SECTOR_SIZE || dat2.length != Disk.SECTOR_SIZE){
			  System.out.println("Sector Check: Incorrect Sector Size");
			  return false; 
		  }
		  for(int i = 0; i < Disk.SECTOR_SIZE; i++){
			  if(dat1[i] != dat2[i]){
				  //System.out.println((char)dat1[i] + " - " + (char)dat2[i]);
				  System.out.println("Sector Check: Data Mismatch");
				  return false;
			  }
		  }
		  return true; 
	}
	  
	private static boolean LogCompare(byte[] dat1, byte[] dat2){
		  if(dat1.length != dat2.length){
			  System.out.println("Log Compare: Logs different sizes");
			  return false; 
		  }
		  for(int i = 0; i < dat1.length; i++){
			  if(dat1[i] != dat2[i]){
				  System.out.println("Log Compare: Data Mismatch");
				  return false;
			  }
		  }
		  return true; 
	}
				  
}
