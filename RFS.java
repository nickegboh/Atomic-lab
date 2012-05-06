/*
 * RFS -- reliable file system
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */
import java.io.IOException;
import java.io.EOFException;
import java.util.ArrayList;
public class RFS{
   private int[] FileDescriptors;
   private TransID[] FileDescriptor_TID;
   private FlatFS fileManager; 
  /*
   * This function is the constructor. If doFormat == false, data stored in previous 
   * sessions must remain stored. If doFormat == true, the system should initialize 
   * the underlying file system to empty. 	
   */
  public RFS(boolean doFormat)
    throws IOException
  {
	  fileManager = new FlatFS(doFormat);
	  FileDescriptors = new int[Common.MAX_FD+1];
	  FileDescriptor_TID = new TransID[Common.MAX_FD+1];
	  for(int i = 0; i < FileDescriptors.length; i++)
		  FileDescriptors[i] = -1;
	  if(doFormat){
		  TransID tempID = fileManager.beginTrans();
		  int tempInode = fileManager.createFile(tempID);
		  char[] rootName = {'r','o','o','t'};
		  DirEnt root = new DirEnt(tempInode, rootName, tempInode);
		  byte[] metaData = root.getMetaData();
		  byte[] directory = root.toByteArray();
		  fileManager.write(tempID, tempInode, 0, directory.length, directory);
		  fileManager.writeFileMetadata(tempID, tempInode, metaData);
	 }
  }
  
  /*
   * This function atomically creates a new file with the name filename. Filename is a 
   * full pathname (starting with "/").   If the parameter openIt is true, the function
   *  returns a file descriptor of the open file corresponding to the newly created file;
   *   in this case, the initial create(), a sequence of zero or more read() and write()
   *    calls to that file, and a final close() should all occur within a single transaction.
   */
  public int createFile(String filename, boolean openIt)
    throws IOException, IllegalArgumentException
  {
	  String[] names = seperatePath(filename);
	  
	  //read root node
	  TransID transid = fileManager.beginTrans();
	  byte[] tempMeta = new byte[PTree.METADATA_SIZE];
	  fileManager.readFileMetadata(transid, 0, tempMeta);
	  int templength = DirEnt.getLengthBytes(tempMeta);
	  byte[] tempBuff = new byte[templength];
	  fileManager.read(transid, 0, 0, templength, tempBuff);
	  DirEnt tempDirectory = new DirEnt(tempBuff, tempMeta);
	  int tempInum = -1;
	  
	  for(int i = 0; i < names.length; i++){
		  char[] thisName = names[i].toCharArray();
		  if(i == names.length-1){
			  int tempInode = fileManager.createFile(transid);			  
			  tempDirectory.addFile(tempInode, names[i].toCharArray());
			  if(openIt){
				  fileManager.commitTrans(transid);
				  return open(filename);
			  }
		  }
		  else{
			  tempInum = tempDirectory.getInum(thisName);
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return -1;
			  }
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  templength = DirEnt.getLengthBytes(tempMeta);
			  tempBuff = new byte[templength];
			  fileManager.read(transid, 0, 0, templength, tempBuff);
			  tempDirectory = new DirEnt(tempBuff, tempMeta);
		  }
		  
	  }
	  fileManager.commitTrans(transid);
	  return -1;
  }
  
  /*
   * This function atomically creates a directory entry with the name dirname. 
   * As before, the name is interpreted as a full pathname.
   */
  public void createDir(String dirname)
    throws IOException, IllegalArgumentException
  {
	  String[] names = seperatePath(dirname);
	  
	  //read root node
	  TransID transid = fileManager.beginTrans();
	  byte[] tempMeta = new byte[PTree.METADATA_SIZE];
	  fileManager.readFileMetadata(transid, 0, tempMeta);
	  int templength = DirEnt.getLengthBytes(tempMeta);
	  byte[] tempBuff = new byte[templength];
	  fileManager.read(transid, 0, 0, templength, tempBuff);
	  DirEnt tempDirectory = new DirEnt(tempBuff, tempMeta);
	  int tempInum = -1;
	  
	  for(int i = 0; i < names.length; i++){
		  char[] thisName = names[i].toCharArray();
		  if(i == names.length-1){
			  int tempInode = fileManager.createFile(transid);
			  DirEnt newDir = new DirEnt(tempInode, names[i].toCharArray(), tempDirectory.getInum());
			  byte[] metaData = newDir.getMetaData();
			  byte[] directory = newDir.toByteArray();
			  fileManager.write(transid, tempInode, 0, directory.length, directory);
			  fileManager.writeFileMetadata(transid, tempInode, metaData);
		  }
		  else{
			  tempInum = tempDirectory.getInum(thisName);
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  templength = DirEnt.getLengthBytes(tempMeta);
			  tempBuff = new byte[templength];
			  fileManager.read(transid, 0, 0, templength, tempBuff);
			  tempDirectory = new DirEnt(tempBuff, tempMeta);
		  }
		  
	  }
	  fileManager.commitTrans(transid);
  }

  /*
   * This function atomically removes the entry specified by the name. The name is 
   * interpreted as usual. If the name corresponds to a file and the file is not 
   * currently open, it is deleted and the corresponding resources are reclaimed. 
   * If name corresponds to a directory, it is deleted only if it is an empty directory. 
   */
  public void unlink(String filename)
    throws IOException, IllegalArgumentException
  {
	  String[] names = seperatePath(filename);
	  
	  //read root node
	  TransID transid = fileManager.beginTrans();
	  byte[] tempMeta = new byte[PTree.METADATA_SIZE];
	  fileManager.readFileMetadata(transid, 0, tempMeta);
	  int templength = DirEnt.getLengthBytes(tempMeta);
	  byte[] tempBuff = new byte[templength];
	  fileManager.read(transid, 0, 0, templength, tempBuff);
	  DirEnt tempDirectory = new DirEnt(tempBuff, tempMeta);
	  int tempInum = -1;
	  
	  for(int i = 0; i < names.length; i++){
		  char[] thisName = names[i].toCharArray();
		  if(i == names.length-1){
			  // this is final level of tree
			  tempInum = tempDirectory.getInum(names[i].toCharArray());
			  //if file doesn't exist return
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  // if file has active file descriptor return 
			  for(int j = 0; j < FileDescriptors.length; j++)
				  if(FileDescriptors[j] == tempInum){
					  fileManager.commitTrans(transid);
					  return;
				  }
			  //if file is directory return 
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  if(DirEnt.isDirectory(tempMeta)){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  //remove file
			  fileManager.deleteFile(transid, tempInum);
			  tempDirectory.remove(names[i].toCharArray());				  
		  }
		  else{
			  // go to next level of file system tree
			  tempInum = tempDirectory.getInum(thisName);
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  templength = DirEnt.getLengthBytes(tempMeta);
			  tempBuff = new byte[templength];
			  fileManager.read(transid, 0, 0, templength, tempBuff);
			  tempDirectory = new DirEnt(tempBuff, tempMeta);
		  }
		  
	  }	  
  }
  
  /*
   * This function atomically changes the name of an existing file oldName into a 
   * new file newName.  Assumes same directory. 
   */
  public void rename(String oldName, String newName)
    throws IOException, IllegalArgumentException
  {
	  String[] names = seperatePath(oldName);
	  String[] NewNames = seperatePath(newName);	  
	  
	  //read root node
	  TransID transid = fileManager.beginTrans();
	  byte[] tempMeta = new byte[PTree.METADATA_SIZE];
	  fileManager.readFileMetadata(transid, 0, tempMeta);
	  int templength = DirEnt.getLengthBytes(tempMeta);
	  byte[] tempBuff = new byte[templength];
	  fileManager.read(transid, 0, 0, templength, tempBuff);
	  DirEnt tempDirectory = new DirEnt(tempBuff, tempMeta);
	  int tempInum = -1;
	  
	  for(int i = 0; i < names.length; i++){
		  char[] thisName = names[i].toCharArray();
		  if(i == names.length-1){
			  // this is final level of tree
			  tempInum = tempDirectory.getInum(names[i].toCharArray());
			  //if file doesn't exist return
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  //if file is directory return 
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  if(DirEnt.isDirectory(tempMeta)){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  //rename file
			  tempDirectory.renameFile(names[i].toCharArray(), NewNames[i].toCharArray());
		  }
		  else{
			  // go to next level of file system tree
			  tempInum = tempDirectory.getInum(thisName);
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return;
			  }
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  templength = DirEnt.getLengthBytes(tempMeta);
			  tempBuff = new byte[templength];
			  fileManager.read(transid, 0, 0, templength, tempBuff);
			  tempDirectory = new DirEnt(tempBuff, tempMeta);
		  }
		  
	  }	
  }

  /*
   * This function performs a lookup on the file or directory whose name is specified 
   * by name. The character string specified by name must start with "/" making name a 
   * full pathname that starts from the root of the file system. The call returns a file 
   * descriptor that can be used later to refer to the file or directory specified by the 
   * search path. The function fails if name does not specify an existing file, if no file 
   * descriptors are free, or if the name corresponds to a directory. All reads and writes 
   * to the open file are part of a single transaction.
   * 
   * If function fails, returns -1.
   */
  public int open(String filename)
    throws IOException, IllegalArgumentException
  {
	  String[] names = seperatePath(filename);
	  
	  //read root node
	  TransID transid = fileManager.beginTrans();
	  byte[] tempMeta = new byte[PTree.METADATA_SIZE];
	  fileManager.readFileMetadata(transid, 0, tempMeta);
	  int templength = DirEnt.getLengthBytes(tempMeta);
	  byte[] tempBuff = new byte[templength];
	  fileManager.read(transid, 0, 0, templength, tempBuff);
	  DirEnt tempDirectory = new DirEnt(tempBuff, tempMeta);
	  int tempInum = -1;
	  
	  for(int i = 0; i < names.length; i++){
		  char[] thisName = names[i].toCharArray();
		  if(i == names.length-1){
			  // this is final level of tree
			  tempInum = tempDirectory.getInum(names[i].toCharArray());
			  //if file doesn't exist return
			  if(tempInum == -1){
				  fileManager.commitTrans(transid);
				  return -1;
			  }
			  //if file is directory return 
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  if(DirEnt.isDirectory(tempMeta)){
				  fileManager.commitTrans(transid);
				  return -1;
			  }
			  //find open file descriptor
			  int j = 0; 
			  for(j = 0; j < FileDescriptors.length; j++)
				  if(FileDescriptors[j] == -1)
					  break;
			  //set file descriptor and return file
			  FileDescriptors[j] = tempInum;
			  FileDescriptor_TID[j] = transid;
			  return j;  
		  }
		  else{
			  // go to next level of file system tree
			  tempInum = tempDirectory.getInum(thisName);
			  if(tempInum == -1)
				  return -1;
			  fileManager.readFileMetadata(transid, tempInum, tempMeta);
			  templength = DirEnt.getLengthBytes(tempMeta);
			  tempBuff = new byte[templength];
			  fileManager.read(transid, 0, 0, templength, tempBuff);
			  tempDirectory = new DirEnt(tempBuff, tempMeta);
		  }
		  
	  }
	  fileManager.commitTrans(transid);
	  return -1;
  }

  /*
   * This function closes the open file indicated by the file descriptor fd and commits 
   * any updates. Subsequent access to files through the fd descriptor must return an 
   * error, until the fd is reused again in an open call. Also, any resources used to 
   * support the file descriptor should be reclaimed at this point.
   */
  public void close(int fd)
    throws IOException, IllegalArgumentException
  {
	  FileDescriptors[fd] = -1; 
	  fileManager.commitTrans(FileDescriptor_TID[fd]);
	  return; 
  }

  /*
   * This function reads count bytes from the file specified by fd into the buffer 
   * specified by buffer. The parameter offset specifies the starting location within 
   * the file where the data should be read. Upon success, the function returns the 
   * number of bytes read (this number can be less than count if no more bytes are 
   * available from the position specified by offset until the end of the file).
   */
  public int read(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    return fileManager.read(FileDescriptor_TID[fd], FileDescriptors[fd], offset, count, buffer);
  }

  /*
   * This function writes count bytes from the buffer specified by buffer into the file 
   * specified by fd. The parameter offset specifies the starting location within the 
   * file where the data should be written. Attempting to write beyond the end of file 
   * should extend the size of the file to accommodate the new data. These writes will 
   * commit when the file is closed.
   */
  public void write(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
	  fileManager.write(FileDescriptor_TID[fd], FileDescriptors[fd], offset, count, buffer);
  }

  /*
   * This function atomically reads the entries that exist in the directory specified by 
   * dirname. and returns the result in an array of String objects. 
   */
  public String[] readDir(String dirname)
    throws IOException, IllegalArgumentException
  {
	  String[] names = seperatePath(dirname);
	  
	  //read root node
	  TransID transid = fileManager.beginTrans();
	  byte[] tempMeta = new byte[PTree.METADATA_SIZE];
	  fileManager.readFileMetadata(transid, 0, tempMeta);
	  int templength = DirEnt.getLengthBytes(tempMeta);
	  byte[] tempBuff = new byte[templength];
	  fileManager.read(transid, 0, 0, templength, tempBuff);
	  DirEnt tempDirectory = new DirEnt(tempBuff, tempMeta);
	  int tempInum = -1;
	  
	  for(int i = 0; i < names.length; i++){
		  char[] thisName = names[i].toCharArray();
		  // go to next level of file system tree
		  tempInum = tempDirectory.getInum(thisName);
		  if(tempInum == -1)
			  return null;
		  fileManager.readFileMetadata(transid, tempInum, tempMeta);
		  templength = DirEnt.getLengthBytes(tempMeta);
		  tempBuff = new byte[templength];
		  fileManager.read(transid, 0, 0, templength, tempBuff);
		  tempDirectory = new DirEnt(tempBuff, tempMeta);
		  // if appropriate directory return contents
		  if(i == names.length-1){
			  return tempDirectory.listContents();
		  }
		  
	 }
	 fileManager.commitTrans(transid);
	 return null;
  }

  /*
   * This function returns the number of bytes contained in the open file identified by fd.
   */
  public int size(int fd)
    throws IOException, IllegalArgumentException
  {
    return (fileManager.ptree.getMaxDataBlockId(FileDescriptor_TID[fd], FileDescriptors[fd])*PTree.BLOCK_SIZE_BYTES);
  }

  /*
   * This function returns the number of data blocks (excluding internal nodes) consumed by 
   * the open file identified by fd.  Notice that space has to consider the existence of 
   * holes while size is not affected by holes in a file.
   */
  public int space(int fd)
    throws IOException, IllegalArgumentException
  {
	return fileManager.ptree.getMaxDataBlockId(FileDescriptor_TID[fd], FileDescriptors[fd]);
  }
  
  private String[] seperatePath(String filename){
	  return filename.substring(1).split("/");
  }




}
