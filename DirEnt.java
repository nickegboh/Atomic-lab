import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/*
 * DirEnt -- fields of a directory entry. Feel free
 * to modify this class as desired.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */
public class DirEnt{
  public final static int MAX_NAME_LEN_CHAR = 16;
  private int inum;
  private char name[] = new char[MAX_NAME_LEN_CHAR];
  private ArrayList<Integer> inodes;
  private ArrayList<char[]> names;

  //
  // Feel free to modify DirEnt as desired
  //
  
  //create new directory 
  public DirEnt(int inum_in, char[] name_in, int inum_parent_in){
	 inum = inum_in;
	 assert(name_in.length <= MAX_NAME_LEN_CHAR);
	 name = DirEnt.extendName(name_in);
	 inodes = new ArrayList<Integer>();
	 names = new ArrayList<char[]>();
	 char[] temp = new char[MAX_NAME_LEN_CHAR];
	 for(int i = 0; i < temp.length; i++){
		 if(i < 2)
			 temp[i] = '.';
		 else
			 temp[i] = (char)0x00;
	 }
	 names.add(temp);
	 temp[1] = (char)0x00;
	 names.add(temp);
	 inodes.add(inum_parent_in);
	 inodes.add(inum_in);
  }
  
  //create directory from bytes
  public DirEnt(byte[] inArray, byte[] metadata){
	  inodes = new ArrayList<Integer>();
	  names = new ArrayList<char[]>();
	  ByteBuffer a = ByteBuffer.wrap(metadata);
	  char tempchar = a.getChar();
	  assert(tempchar == (char)0xFF);
	  tempchar = a.getChar();
	  assert(tempchar == (char)0x00);
	  tempchar = a.getChar();
	  assert(tempchar == (char)0xDD);
	  inum = a.getInt();
	  int array_size = a.getInt();
	  for(int i = 0; i < MAX_NAME_LEN_CHAR; i++)
		  name[i] = a.getChar();
	  ByteBuffer b = ByteBuffer.wrap(inArray);
	  for(int i = 0; i < array_size; i++){
		 char[] temp = new char[MAX_NAME_LEN_CHAR];
	  	 for(int j = 0; j < MAX_NAME_LEN_CHAR; j++)
	  		 temp[j] = b.getChar();
	  	 names.add(temp);
	  }
	  for(int i = 0; i < array_size; i++){
	  	 inodes.add(b.getInt());
	  }
  }
  
  // get this directory in the form of a byte array without meta data
  public byte[] toByteArray(){
	  byte[] toReturn = new byte[names.size()*4 + inodes.size()*16*2];
	  assert(names.size() == inodes.size());
	  ByteBuffer b = ByteBuffer.wrap(toReturn);
	  for(int i = 0; i < names.size(); i++){
		  for(int j = 0; j < MAX_NAME_LEN_CHAR; j++)
			  b.putChar(names.get(i)[j]);
	  }
	  for(int i = 0; i < inodes.size(); i++)
		  b.putInt(inodes.get(i));
	  toReturn = b.array();
	  return toReturn;
  }
  
  public byte[] getMetaData(){
	  byte[] toReturn = new byte[PTree.METADATA_SIZE];
	  ByteBuffer b = ByteBuffer.wrap(toReturn);
	  b.putChar((char)0xFF);
	  b.putChar((char)0x00);
	  b.putChar((char)0xDD); 
	  b.putInt(inum);
	  b.putInt(names.size());
	  assert(names.size() == inodes.size());
	  for(int i = 0; i < MAX_NAME_LEN_CHAR; i++)
		  b.putChar(name[i]);
	  toReturn = b.array();
	  return toReturn;
  }
  
  public int getInum() { return inum; }
  public int getParentInum() { return inodes.get(0); }
  
  // add file to directory
  public void addFile(int inode, char[] name_in){
	  char[] name = DirEnt.extendName(name_in);
	  names.add(name);
	  inodes.add(inode);
  }
  
  // get inum for specified name return -1 if file does not exist. 
  public int getInum(char[] name_in){
	  assert(names.size() == inodes.size());
	  char[] name = DirEnt.extendName(name_in);
	  int i = 0;
	  for(i = 0; i < names.size(); i++){
		  if(Arrays.equals(name, names.get(i)))
			  break;
	  }
	  if(i == names.size())
		  return -1;
	  else return inodes.get(i);
  }
  
  public void remove(char[] name_in){
	  assert(names.size() == inodes.size());
	  char[] name = DirEnt.extendName(name_in);
	  int i = 0;
	  for(i = 0; i < names.size(); i++){
		  if(Arrays.equals(name, names.get(i)))
			  break;
	  }
	  if(i == names.size())
		  return;
	  else {
		  names.remove(i);
		  inodes.remove(i);
	  }
  }
  
  public boolean renameFile(char[] name_in, char[] new_name_in){
	  assert(names.size() == inodes.size());
	  assert(new_name_in.length <= MAX_NAME_LEN_CHAR);
	  char[] name = DirEnt.extendName(name_in);
	  char[] new_name = DirEnt.extendName(new_name_in);
	  int i = 0;
	  for(i = 0; i < names.size(); i++){
		  if(Arrays.equals(name, names.get(i)))
			  break;
	  }
	  if(i == names.size())
		  return false; 
	  else names.set(i, new_name);
	  return true;
  }
  
  public String[] listContents(){
	  String[] toReturn = new String[names.size()];
	  for(int i = 0; i < names.size(); i++)
		  toReturn[i] = names.get(i).toString();	  
	  return toReturn;
  }
  
  /*
  // given full extended name shrink to readable name
  private static char[] shrinkName(char[] inString){
	  int firstNull = 0; 
	  for(firstNull = 0; firstNull < inString.length; firstNull++)
		  if(inString[firstNull] == 0x00)
			  break;
	  char[] result = new char[firstNull];
	  for(int i = 0; i < firstNull; i++)
		  result[i] = inString[i];
	  return result;
  }
  */
  
  // extend given string to full max name length
  private static char[] extendName(char[] name_in) {
	  char[] name = new char[MAX_NAME_LEN_CHAR];
	  for(int i = 0; i < name.length; i++){
			 if(i >= name_in.length)
				 name[i] = (char)0X00;
			 else
				 name[i] = name_in[i];
		 }
	  return name; 	  
  }
  public static boolean isDirectory(byte[] metaData){
	  ByteBuffer a = ByteBuffer.wrap(metaData);
	  boolean result = true;
	  if(a.getChar() != (char)0xFF)
		  result = false;
	  if(a.getChar() != (char)0x00)
		  result = false; 
	  if(a.getChar() != (char)0xDD)
		  result = false; 
	  return result; 
  }
  
  //get length of directory to be read in bytes from a directory meta data
  public static int getLengthBytes(byte[] metaData){
	  ByteBuffer a = ByteBuffer.wrap(metaData);
	  char temp = a.getChar();
	  assert(temp == (char)0xFF);
	  temp = a.getChar();
	  assert(temp == (char)0x00);
	  temp = a.getChar();
	  assert(temp == (char)0xDD);
	  a.getInt();
	  int array_size = a.getInt();
	  return ((array_size * 4) + (array_size*16*2));
  }
  
  
}
