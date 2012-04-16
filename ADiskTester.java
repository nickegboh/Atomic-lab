/*
 * ADiskTester.java
 *
 * Tests for various parts of the project, 
 * like, transaction, Adisk & stuff
 */
 
import java.util.concurrent.locks.Condition;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.*;
import java.nio.ByteBuffer;

//Do not use till we have something solid
public class ADiskTester {
  public static void main(String args[])
  {
    System.out.println("Start to Test TRANSACTION");
    Transaction[] trans = new Transaction[500];// 500 should be sufficient
    for(int i = 0; i < 500; ++i) {
      trans[i] = new Transaction();
    }

    byte[] bufA = new byte[512];
    byte[] bufB = new byte[512];
    byte[] bufC = new byte[512];
    for(int i = 0; i < 512; i++) {
      bufA[i] = (byte)'A';
      bufB[i] = (byte)'B';
      bufC[i] = (byte)'C';
    }
    
    /* We are adding 1000 buffers to each transaction
     	If tid % 3 = 0, then put bufA
     	If tid % 3 = 1, then put bufB
     	If tid % 3 = 2, then put bufC	*/
    int secNumCounter = 0;
    for(int j = 0; j < 1000; j++) {
      for(int i = 0; i < 500; i++) {
        if(i % 3 == 0) {
          try {
            trans[i].addWrite(secNumCounter++, bufA);
          }
          catch(IndexOutOfBoundsException e) {
            assert secNumCounter > ADisk.getNSectors() : "Sector " + secNumCounter;
          }
        }
        else if(i % 3 == 1) {
          try {
            trans[i].addWrite(secNumCounter++, bufB);
          }
          catch(IndexOutOfBoundsException e) {
            assert secNumCounter > ADisk.getNSectors() : "Sector " + secNumCounter;
          }
        }
        else if(i % 3 == 2) {
          try {
            trans[i].addWrite(secNumCounter++, bufC);
          }
          catch(IndexOutOfBoundsException e) {
            assert secNumCounter > ADisk.getNSectors() : "Sector " + secNumCounter;
          }
        }
      }
    }
//////////////////////////////////////////////////////////////////////////////////

    for(int i = 250; i < 500; ++i) {
        try {
			trans[i].abort();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
      }

    try {
        for(int i = 250; i < 500; i++) {
          try {
            trans[i].addWrite(0, bufA);
            System.out.println("FAILED trans..addWrite expecting " +
                               "IllegalArgumentException caused by writing " +
                               "aborted transaction");
            System.exit(1);
          }
          catch(IllegalArgumentException e) {}//Nothing to be done Expecting exception
          try {
            trans[i].checkRead(0, bufA);
            System.out.println("FAILED trans..checkRead expecting " +
                               "IllegalArgumentException caused by reading " +
                               "aborted transaction");
            System.exit(1);
          }
          catch(IllegalArgumentException e) {}//Nothing to be done Expecting exception
        }
      }
      catch(IllegalArgumentException e) {
        System.out.println(e);
        System.exit(1);
      }

//////////////////////////////////////////////////////////////////////////////////////
    for(int i = 0; i < 500; i++) {
      try {
        trans[i].commit();
      }
      catch(IllegalArgumentException e) {
        if(i < 250) {
          System.out.println("FAILED cannot commit so aborted transaction");
          System.exit(1);
        }
      }
      catch(IOException e) {
        System.out.println("FAILED commit so threw IOException " + e);
        System.exit(1);
      }
    }
    

    System.out.println("Get NSector updates from each transaction");
    for(int i = 0; i < 500; ++i) {
      try {
        System.out.print(i + ":" + trans[i].getNUpdatedSectors() + " ");
        if(i % 10 == 0) {
          System.out.println();
        }
      }
      catch(IllegalArgumentException e) {
        if(i < 250) {
          System.out.println("FAILED getNUpdatedSectors so threw IOException " + e);
          System.exit(1);
        }
      }
    }
    System.out.println();

    try {
      ByteBuffer header = trans[5].getHeader();
      ByteBuffer body   = trans[5].getBody();
      ByteBuffer footer = trans[5].getFooter();

      long transId = header.getLong();
      int numSec   = header.getInt();
      System.out.println("trans Id: " + transId);
      System.out.println("num sectors: " + numSec);
      System.out.println("Sector IDs: ");
      for(int i = 0; i < numSec; i++) {
        System.out.print(header.getInt() + " ");
        if(i % 40 == 39) {
          System.out.println();
        }
      }
      System.out.println();
      
      System.out.println("sectors: ");
      for(int i = 0; i < (512 * numSec); i++) {
        if(i % 512 == 0) {
          System.out.println("sector " + (i / 512));
        }
        System.out.print(body.getChar() + " ");
        if(i % 40 == 39) {
          System.out.println();
        }
      }
      System.out.println();
      System.out.println("footer: " + footer.getLong());


    } catch(java.nio.BufferUnderflowException e) {
      System.out.println(e);
      System.exit(1);
    }
    System.out.println("Transactions Done");
  }
}