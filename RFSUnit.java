import java.io.IOException;


public class RFSUnit {
	private static RFS rfs;
	
	public static void main(String args[]) throws IllegalArgumentException, IOException
	  {
		  
		  //disk = new ADisk(true);
		  rfs = new RFS(true);
		  int passcount = 0; 
		  int failcount = 0; 
		
		  //Test Commit Transaction
		  if(testInitialwrite())
			  passcount++;
		  else 
			  failcount++;
		
		  
		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
		System.exit(0);
	  }

	 public static boolean testInitialwrite() 
	 	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{     
	              boolean testGood = true;
	              

	              
	              int fd = rfs.createFile("/filename2.exe", true);
                  String[] expected = {".", "..", "filename.exe", "dir2", "filename2.exe"};
                  
                  byte[] buffer = new byte[PTree.BLOCK_SIZE_BYTES];
        		  for(int i=0; i<buffer.length; i++){
        			  buffer[i] = (byte)i;
        		  }
                  //rfs.read(fd, 0, 10, buffer);
                  
                  assert rfs.size(fd) == 0;
                  assert rfs.space(fd) == 0;
                  
                  
                          
                  rfs.write(fd, 0, 10, buffer);
                  assert rfs.size(fd) == 10;
                  assert rfs.space(fd) == 1024;
                          
                  rfs.write(fd, 1000, 10, buffer);
                  assert rfs.size(fd) == 1010;
                  assert rfs.space(fd) == 1024;
                          
                  rfs.write(fd, 10000, 10, buffer);
                  assert rfs.size(fd) == 10010;
                  assert rfs.space(fd) == 2048;
                  
                  rfs.close(fd);
                 
	              if(testGood){
	                    System.out.println("Test InitialRead: Passed!");
	              		return true;
	              }
	              else{
	                      System.out.println("Test InitialRead: Fails!");
	                      return false;
	              }
	              	
	  }
}