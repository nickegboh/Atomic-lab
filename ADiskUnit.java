import java.util.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class ADiskUnit {
	
	  private static ADisk disk;
	  private static ADisk disk_2;
	  	
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
		
		//Test Single commit with one TID
		if(testSingleCommitWithOneTID())
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
		
	    //Test ADisk Basic
		if(ADiskTestbasic())
		  passcount++;
		else 
		  failcount++;
		
	    //Test Recovery Short
		if(recoveryTestshort())
		  passcount++;
		else 
		  failcount++;
		
		//More recovery testing
		if(recovery())
			passcount++;
		else
			failcount++;
		
		/* every thing implemented, so writing more tests   */
		
		//Does a bunch of read, writes & commits to sector, then compare the content
		//and then checks the disk recovery
		if(adiskTest())
			passcount++;
		else
			failcount++;
		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
		System.exit(0);
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

	  public static byte[] generateSector(byte input){
          byte [] sampleSector = new byte[512];
            for(int i = 0; i < sampleSector.length; i++)
                    sampleSector[i] = input;
            return sampleSector;
  }
  
  public static boolean testSingleCommitWithOneTID(){                //Java presets the Hard Drive to 0's 
          try{
                  ADisk tester = new ADisk(true);
                  tester.setFailureProb(0.0f);
                  TransID t1 =  tester.beginTransaction();
                  
                  byte [] sampleSectorT1 = generateSector((byte) 0);
                    for(int i = 0; i < sampleSectorT1.length; i++)
                            sampleSectorT1[i] = 0;
              tester.writeSector(t1, 1024, sampleSectorT1);
                 
              tester.commitTransaction(t1);
              
              TransID t2 =  tester.beginTransaction();
                  byte[] resultT1 = new byte[512];
                          tester.readSector(t2, 1024, resultT1);
                  
                  boolean testGood = true;
                  for(int i = 0; i< 512; i++)
                  {
                          //System.out.println(resultT1[i]+", "+sampleSectorT1[i]);
                          if(resultT1[i] != sampleSectorT1[i])
                                  testGood = false;
                  }
                  
                  if(testGood){
                          System.out.println("TestSingleCommitWithOneTID Succeeds");
                          return true;
                  }
                  else{
                          System.out.println("TestSingleCommitWithOneTID Fails");
                          return false;
                  }
          }
          catch(Exception e){
                          System.out.println("TestSingleCommitWithOneTID Fails");
                          return false;
          }
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
	  
	  public static boolean testInitialRead() throws IllegalArgumentException, IndexOutOfBoundsException, IOException{     
//	      try{
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
	              	
//	      }
//	      catch(Exception e){
//	    	  System.out.println("TestInitialRead Fails");
//	    	  return false;
//	      }
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
	  
	  public static boolean ADiskTestbasic() throws IllegalArgumentException, IOException {
		  boolean returnBool = true; 
		  
		  //create test transactions
		  TransID temp1 = disk.beginTransaction();
		  TransID temp2 = disk.beginTransaction();
		  
		  //create test data
		  byte[] dat1 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat2 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat3 = new byte[Disk.SECTOR_SIZE];
		  byte[] dat4 = new byte[Disk.SECTOR_SIZE];
		  byte[] datret = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat1.length; i++)
			  dat1[i] = 'a';
		  for(int i = 0; i < dat2.length; i++)
			  dat2[i] = 'b';
		  for(int i = 0; i < dat3.length; i++)
			  dat3[i] = 'c';
		  for(int i = 0; i < dat4.length; i++)
			  dat4[i] = 'd';
		  
		  disk.writeSector(temp1, 15, dat1);
		  disk.writeSector(temp1, 17, dat2);
		  disk.commitTransaction(temp1);
		  
		  //test read with just temp1
		  disk.readSector(temp1, 15, datret);
		  if(!SectorCheck(datret, dat1)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: First Read Mismatch");
		  }
		  disk.readSector(temp1, 17, datret);
		  if(!SectorCheck(datret, dat2)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: Second Read Mismatch");
		  }
		  
		  //add more writes to uncommited transactions and test read from first transactoin and uncommitted transactions.
		  disk.writeSector(temp2, 15, dat3);
		  disk.writeSector(temp2, 17, dat4);
		  
		  //test read with just temp1
		  disk.readSector(temp1, 15, datret);
		  if(!SectorCheck(datret, dat1)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: Third Read Mismatch");
		  }
		  disk.readSector(temp1, 17, datret);
		  if(!SectorCheck(datret, dat2)){
			  returnBool = false;
			  System.out.println("Test ADisk Basic: Fourth Read Mismatch");
		  }
		  
		  //test read with just temp1
		  disk.readSector(temp2, 15, datret);
		  if(!SectorCheck(datret, dat3)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: Fifth Read Mismatch");
		  }
		  disk.readSector(temp2, 17, datret);
		  if(!SectorCheck(datret, dat4)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: Sixth Read Mismatch");
		  }
		  
		  //test read after committing second transaction
		  disk.commitTransaction(temp2);
		  disk.readSector(temp1, 15, datret);
		  if(!SectorCheck(datret, dat3)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: Fifth Read Mismatch");
		  }
		  disk.readSector(temp1, 17, datret);
		  if(!SectorCheck(datret, dat4)){
			  returnBool = false; 
			  System.out.println("Test ADisk Basic: Sixth Read Mismatch");
		  }
		  
		  if(returnBool)
			  System.out.println("Test ADisk Basic: Passed!");
		  else
			  System.out.println("Test ADisk Basic: Failed!");
		  return returnBool;
	  }
	  
	  // Recovery Tests start here
	  public static boolean recoveryTestshort() throws IllegalArgumentException, IOException {
		  TransID temp1 = disk.beginTransaction();
		  boolean pass = true;
		  
		  byte[] dat1 = new byte[Disk.SECTOR_SIZE];
		  byte[] datresult = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat1.length; i++)
			  dat1[i] = 'a';
		  
		  disk.writeSector(temp1, 15, dat1);
		  disk.commitTransaction(temp1);
		  
		  disk = null;
		  
		  disk = new ADisk(false);
		  
		  temp1 = disk.beginTransaction();
		  disk.readSector(temp1, 15, datresult);
		  
		  if(!SectorCheck(dat1, datresult)){
			  System.out.println("Test Recovery Short: First Recovery Read Failed");
			  pass = false; 
		  }
		  
		  if(pass)
			  System.out.println("Test Recovery Short: Passed!");
		  else
			  System.out.println("Test Recovery Short: Failed!");
		  return pass;
		  
	  }
	  public static boolean recovery() throws IllegalArgumentException, IOException {
		  TransID temp1 = disk.beginTransaction();
		  boolean pass = true;
		  int garbage = temp1.getTidfromTransID();
		  byte[] dat1 = new byte[Disk.SECTOR_SIZE];
		  byte[] datresult = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < dat1.length; i++){
			  if(i == dat1.length-1 || i%2 == 0)
				  dat1[i] = 'W';
			  else
				  dat1[i] = 'a';
		  }
          // Create a brand new, formatted ADisk
          // Set it's fail probability to 100% and do some
          // random writes to crash it.
          
          //disk = new ADisk(true);
          disk.writeSector(temp1, garbage, dat1);
          disk.commitTransaction(temp1);
          
    	   // Now try to recover it
          
          // Check that the data we wrote is still there!
          //
          //////
          disk_2  = new ADisk(false);
		  
		  temp1 = disk_2.beginTransaction();
		  disk_2.readSector(temp1, garbage, datresult);
                  if(!SectorCheck(dat1, datresult)){
        			  System.out.println("Test Recovery: First Recovery Read Crashed");
        			  pass = false; 
        		  }
        		  
        		  if(pass)
        			  System.out.println("Test Recovery: Passed!");
        		  else
        			  System.out.println("Test Recovery: Crashed!");
        		  return pass;
  }

	  
	  private static boolean SectorCheck(byte[] dat1, byte[] dat2){
		  if(dat1.length != Disk.SECTOR_SIZE || dat2.length != Disk.SECTOR_SIZE){
			  System.out.println("Sector Check: Incorrect Sector Size");
			  return false; 
		  }
		  //System.out.println("seccheck: " + (char)dat1[0] + " == " + (char)dat2[0]);
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
	//////////////////////
	 public static boolean adiskTest() throws IllegalArgumentException, IOException {
		 System.out.println("TESTING ADISK");
		 boolean xit_yes = true;
		 disk = new ADisk(true);
	    
	    byte[] b1 = new byte[512]; 
	    byte[] b2 = new byte[512];
	    byte[] b3 = new byte[512]; 
	    byte[] b4 = new byte[512];

	    final byte[] ALL_ZEROS  = new byte[512];
	    final byte[] ALL_ONES   = getOnes();
	    final byte[] ALL_TWOS   = getTwos(); 
	    final byte[] ALL_THREES = getThrees();
	    final byte[] ALL_FOURS  = getFours();
	    final byte[] ALL_FIVES  = getFives();
	    final byte[] ALL_SIXES  = getSixes();
	    final byte[] ALL_SEVENS = getSevens();

	    // make some transactions
	    TransID t1 = disk.beginTransaction();
	    TransID t2 = disk.beginTransaction();
	    TransID t3 = disk.beginTransaction();
	    TransID t4 = disk.beginTransaction();

	    try {
	      disk.writeSector(t1, 1, ALL_ONES);
	      disk.writeSector(t1, 2, ALL_TWOS);
	      disk.writeSector(t2, 1, ALL_THREES);
	      disk.writeSector(t2, 2, ALL_FOURS);
	      disk.writeSector(t3, 1, ALL_FIVES);
	      disk.writeSector(t3, 2, ALL_SIXES);
	      disk.writeSector(t4, 1, ALL_SEVENS);
	      disk.readSector(t2, 1, b1);
	      disk.readSector(t3, 2, b2);
	      disk.commitTransaction(t3);
	      disk.readSector(t2, 1, b3);
	      disk.commitTransaction(t1);
	      disk.readSector(t4, 2, b4);
	      
	    }
	    catch(Exception e) {
	      e.printStackTrace();
	      System.exit(1);
	    }

	    if(!Arrays.equals(b1, getThrees())) {
	      System.out.println("FAILED ADisk test 1");
	      System.out.println("Expecting ALL_THREES, got\n" + Arrays.toString(b1));
	      xit_yes = false;
	    }
	    else {
	      System.out.println("PASSED ADisk test 1");
	     
	    }

	    if(!Arrays.equals(b2, getSixes())) {
	      System.out.println("FAILED ADisk test 2");
	      System.out.println("Expecting ALL_SIXES, got\n" + Arrays.toString(b2));
	      xit_yes = false;
	    }
	    else {
	      System.out.println("PASSED ADisk test 2");
	     
	    }

	    if(!Arrays.equals(b3, getThrees())) {
	      System.out.println("FAILED ADisk test 3");
	      System.out.println("Expecting ALL_THREES, got\n" + Arrays.toString(b3));
	      xit_yes = false;
	    }
	    else {
	      System.out.println("PASSED ADisk test 3");
	      
	    }

	    if(!Arrays.equals(b4, getTwos())) {
	      System.out.println("FAILED ADisk test 4");
	      System.out.println("Expecting ALL_TWOS, got\n" + Arrays.toString(b4));
	      xit_yes = false;
	    }
	    else {
	      System.out.println("PASSED ADisk test 4");
	     
	    }

	    //adisk.close();
	    System.out.println("Simulating ADisk crash");
	    
	    try {
	      // not formatting
	      System.out.println("  STARTING RECOVERY");
	      disk = new ADisk(false);
	      System.out.println("  FINISHED RECOVERY");
	      byte[] b5 = new byte[512];
	      TransID t5 = disk.beginTransaction();
	      disk.readSector(t5, 1, b5);

	      if(!Arrays.equals(b5, getOnes())) {
	        System.out.println("FAILED ADisk test 5");
	        System.out.println("Expecting ALL_ONES from sector 1, got\n" + Arrays.toString(b5));
	        xit_yes = false;
	      }
	      else {
	        System.out.println("PASSED ADisk test 5");
	      }
	    }
	    catch(Exception e) {
	      e.printStackTrace();
	      System.exit(1);
	    }
	    return xit_yes;
	    //adisk.close();
	  }

	/////////////////////
	 static byte[] getZeros() {
		    return new byte[512];
		  }
		  static byte[] getOnes() {
		    byte[] ALL_ONES   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) {
		      ALL_ONES[ii] = 1;
		    }
		    return ALL_ONES;
		  }
		  static byte[] getTwos() {
		    byte[] ALL_TWOS   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) {
		      ALL_TWOS[ii] = 2;
		    }
		    return ALL_TWOS;
		  }
		  static byte[] getThrees() {
		    byte[] ALL_THREES   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) {
		      ALL_THREES[ii] = 3;
		    }
		    return ALL_THREES;
		  }
		  static byte[] getFours() {
		    byte[] ALL_FOURS   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) {
		      ALL_FOURS[ii] = 4;
		    }
		    return ALL_FOURS;
		  }
		  static byte[] getFives() {
		    byte[] ALL_FIVES   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) { 
		      ALL_FIVES[ii] = 5;
		    }
		    return ALL_FIVES;
		  }
		  static byte[] getSixes() {
		    byte[] ALL_SIXES   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) {
		      ALL_SIXES[ii] = 6;
		    }
		    return ALL_SIXES;
		  }
		  static byte[] getSevens() {
		    byte[] ALL_SEVENS   = new byte[512];
		    for(int ii = 0; ii < 512; ++ii) {
		      ALL_SEVENS[ii] = 7;
		    }
		    return ALL_SEVENS;
		  }

	//////////////////// 
}