public class freeBitMap {
	byte[] bitmap; 
	int sectorCount; //num sectors monitored by free bit map
	int reserveSectors;  //num sectors needed to save free bit map
	int freesectors; 
	
	public freeBitMap(int numSecs){	
		sectorCount = numSecs;
		freesectors = numSecs; 
		int reserveSectors = sectorCount / (8 * Disk.SECTOR_SIZE); 
		if(sectorCount % (8 * Disk.SECTOR_SIZE) != 0)
			reserveSectors++;
		bitmap = new byte[reserveSectors * Disk.SECTOR_SIZE];
		for(int i = 0; i < bitmap.length; i++)
			bitmap[i] = 0x00; 
	}
	
	public freeBitMap(int numSecs, byte[] bitMapIn){
		sectorCount  = numSecs;
		reserveSectors = bitMapIn.length / Disk.SECTOR_SIZE;
		bitmap = bitMapIn; 
		freesectors = 0; 
		for(int i = 0; i < (sectorCount / 8); i++){ 
				for(int j = 0; j < 8; j++){
					if(((0x80 >> j) & bitmap[i]) != 0)
						freesectors++;
				}
		}
	}
	
	public void markFull(int sectorNum){
		int row = sectorNum / 8; 
		int col = sectorNum % 8; 
		byte bitMask = (byte)(0x80 >> col);  
		bitmap[row] = (byte) (bitmap[row] | bitMask);
	}
	
	public void markEmpty(int sectorNum){
		int row = sectorNum / 8; 
		int col = sectorNum % 8; 
		byte bitMask = (byte)(0x80 >> col);  
		bitMask = (byte) ~bitMask;
		bitmap[row] = (byte) (bitmap[row] & bitMask);
	}
	
	public boolean isFull(int sectorNum){
		int row = sectorNum / 8; 
		int col = sectorNum % 8; 
		byte bitMask = (byte)(0x80 >> col);  
		return ((bitMask & bitmap[row]) != 0);
	}
	
	public int getNextFree(){
		for(int i = 0; i < (sectorCount / 8); i++)
			if(bitmap[i] != (byte)0xFF){
				int temp2 = bitmap[i];
				int j = 0; 
				for(j = 0; j < 8; j++){
					int temp = ((0x80 >> j) & bitmap[i]);
					if(((0x80 >> j) & bitmap[i]) == 0)
						break;
				}
				return ((i*8) + j);
			}
		return -1; 
	}
	
	public byte[] getSectorsforWrite(){
		return bitmap; 
	}
	
	public int getNumSectorsforWrite(){
		return reserveSectors; 
	}
	
	public int getFreeSectors(){
		return freesectors; 
	}
	
}