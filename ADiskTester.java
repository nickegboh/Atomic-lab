/*
 * ADiskTester.java
 *
 * Tests of ADisk
 */
 
import java.util.concurrent.locks.Condition;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.*;
import java.nio.ByteBuffer;

//Do not use till we have something solid
public class ADiskTester {
  public static void main(String args[])
  {
    System.out.println("TESTING TRANSACTION");
    Transaction[] trans = new Transaction[500];
    for(int i = 0; i < 500; ++i) {
      trans[i] = new Transaction();
    }

    byte[] bufA = new byte[512];
    byte[] bufB = new byte[512];
    byte[] bufC = new byte[512];
    for(int i = 0; i < 512; ++i) {
      bufA[i] = (byte)'A';
      bufB[i] = (byte)'B';
      bufC[i] = (byte)'C';
    }
    
    // add 1000 buffers to each transaction
    // tid % 3 = 0, add bufA
    // tid % 3 = 1, add bufB
    // tid % 3 = 2, add bufC
    int sectorNumCount = 0;
    for(int j = 0; j < 1000; ++j) {
      for(int i = 0; i < 500; ++i) {
        if(i % 3 == 0) {
          try {
            trans[i].addWrite(sectorNumCount++, bufA);
          }
          catch(IndexOutOfBoundsException e) {
            assert sectorNumCount > ADisk.getNSectors() : "sector " + sectorNumCount;
          }
        }
        else if(i % 3 == 1) {
          try {
            trans[i].addWrite(sectorNumCount++, bufB);
          }
          catch(IndexOutOfBoundsException e) {
            assert sectorNumCount > ADisk.getNSectors() : "sector " + sectorNumCount;
          }
        }
        else if(i % 3 == 2) {
          try {
            trans[i].addWrite(sectorNumCount++, bufC);
          }
          catch(IndexOutOfBoundsException e) {
            assert sectorNumCount > ADisk.getNSectors() : "sector " + sectorNumCount;
          }
        }
      }
    }


//    for(int i = 250; i < 500; ++i) {
//      trans[i].abort();
//    }

    for(int i = 250; i < 500; ++i) {
      try {
        trans[i].addWrite(0, bufA);
        System.out.println("FAILED transaction.addWrite expecting " +
                           "IllegalArgumentException caused by writing " +
                           "aborted transaction");
        System.exit(1);
      }
      catch(IllegalArgumentException e) {
        // Do nothing. Expecting exception
      }
      try {
        trans[i].checkRead(0, bufA);
        System.out.println("FAILED transaction.checkRead expecting " +
                           "IllegalArgumentException caused by reading " +
                           "aborted transaction");
        System.exit(1);
      }
      catch(IllegalArgumentException e) {
        // Do nothing. Expecting exception
      }
    }

    for(int i = 0; i < 500; ++i) {
      try {
        trans[i].commit();
      }
      catch(IllegalArgumentException e) {
        if(i < 250) {
          System.out.println("FAILED cannot commit aborted transaction");
          System.exit(1);
        }
      }
      catch(IOException e) {
        System.out.println("FAILED commit threw IOException " + e);
        System.exit(1);
      }
    }
    

    System.out.println("Getting NSector updates from each transaction");
    for(int i = 0; i < 500; ++i) {
      try {
        System.out.print(i + ":" + trans[i].getNUpdatedSectors() + " ");
        if(i % 10 == 0) {
          System.out.println();
        }
      }
      catch(IllegalArgumentException e) {
        if(i < 250) {
          System.out.println("FAILED getNUpdatedSectors threw IOException " + e);
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
      for(int i = 0; i < numSec; ++i) {
        System.out.print(header.getInt() + " ");
        if(i % 40 == 39) {
          System.out.println();
        }
      }
      System.out.println();
      
      System.out.println("sectors: ");
      for(int i = 0; i < (512 * numSec); ++i) {
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
    System.out.println("Transactions complete");

    System.out.println("\\o/");
  }
}