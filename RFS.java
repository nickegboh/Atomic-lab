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
public class RFS{

  /*
   * This function is the constructor. If doFormat == false, data stored in previous 
   * sessions must remain stored. If doFormat == true, the system should initialize 
   * the underlying file system to empty. 	
   */
  public RFS(boolean doFormat)
    throws IOException
  {
  }
  
  /*
   * This function atomically creates a new file with the name filename. Filename is a 
   * full pathname (starting with "/").   If the parameter openIt is true, the function
   *  returns a file descriptor of the open file corresponding to the newly created file;
   *   in this case, the initial create(), a sequence of zero or more read() and write()
   *    calls to that file, and a final close() should all occur within a single transaction.
   */
  public void createFile(String filename, boolean openIt)
    throws IOException, IllegalArgumentException
  {
  }
  
  /*
   * This function atomically creates a directory entry with the name dirname. 
   * As before, the name is interpreted as a full pathname.
   */
  public void createDir(String dirname)
    throws IOException, IllegalArgumentException
  {
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
  }
  
  /*
   * This function atomically changes the name of an existing file oldName into a 
   * new file newName
   */
  public void rename(String oldName, String newName)
    throws IOException, IllegalArgumentException
  {
  }

  /*
   * This function performs a lookup on the file or directory whose name is specified 
   * by name. The character string specified by name must start with "/" making name a 
   * full pathname that starts from the root of the file system. The call returns a file 
   * descriptor that can be used later to refer to the file or directory specified by the 
   * search path. The function fails if name does not specify an existing file, if no file 
   * descriptors are free, or if the name corresponds to a directory. All reads and writes 
   * to the open file are part of a single transaction.
   */
  public int open(String filename)
    throws IOException, IllegalArgumentException
  {
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
    return -1;
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
  }

  /*
   * This function atomically reads the entries that exist in the directory specified by 
   * dirname. and returns the result in an array of String objects. 
   */
  public String[] readDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    return null;
  }

  /*
   * This function returns the number of bytes contained in the open file identified by fd.
   */
  public int size(int fd)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }

  /*
   * This function returns the number of data blocks (excluding internal nodes) consumed by 
   * the open file identified by fd.  Notice that space has to consider the existence of 
   * holes while size is not affected by holes in a file.
   */
  public int space(int fd)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }




}
