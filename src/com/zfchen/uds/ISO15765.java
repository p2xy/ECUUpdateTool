package com.zfchen.uds;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.bluetooth.BluetoothSocket;

public class ISO15765 {
	
	/* ����㣺CAN���ĵ�֡���  */
	enum ISO15765FrameType{
		SINGLE_FRAME,
		FIRST_FRAME,
		CONSECUTIVE_FRAME,
		FLOW_CONTROL_FRAME,
		INVALID_FRAME};
	
	/*-------CAN���Ļ����ඨ��--------*/
	public class Item{	//ÿ�����ݳ���Ϊ12���ֽڣ���ʽΪ�� 0xee(һ����ʼ�ֽ�)+CAN ID��2���ֽڣ�+���ݣ�8���ֽڣ�+У��ͣ�һ���ֽڣ�
		public byte[] data = new byte[12];
	}
	
	public class CANFrameBuffer{
		protected ArrayList<Item> frame = null;
		
		public CANFrameBuffer(){
			super();
			frame = new ArrayList<Item>();
		}
		
		public ArrayList<Item> getFrame() {
			return frame;
		}

		public void setFrame(ArrayList<Item> frame) {
			this.frame = frame;
		}
	}
	
	ArrayList<Integer> CANReceiveBuffer = null;
	BluetoothSocket socket;
	
	int request_can_id = 0;
	int response_can_id = 0;
	ArrayList<Byte> receiveData;
	ArrayList<Byte> sendData;
	
	public ArrayList<Integer> getCANReceiveBuffer() {
		return CANReceiveBuffer;
	}

	CANFrameBuffer frameBuffer = null;
	OutputStream outStream;
	InputStream inStream;
	
	public ISO15765(BluetoothSocket bTsocket, int request_id, int response_id) {
		super();
		CANReceiveBuffer = new ArrayList<Integer>();
		frameBuffer = new CANFrameBuffer();
		this.socket = bTsocket;
		this.request_can_id = request_id;
		this.response_can_id = response_id;
	}
	
	public CANFrameBuffer getFrameBuffer() {
		return frameBuffer;
	}
	
	public void setSendData(ArrayList<Byte> sendData) {
		this.sendData = sendData;
	}

	public ArrayList<Byte> getReceiveData() {
		return receiveData;
	}

	/*---------------ISO15765Э��Ĵ���㷨(Ӧ�ò�-->�����)------------------*/
	/**
	 * @param al �����������
	 * @param sendBuf ���ͻ�����
	 * @param CANID �ñ��ĵ�ID�ţ������ĸ��ڵ㣩
	 */
	public void PackCANFrameData(ArrayList<Byte> al, CANFrameBuffer sendBuf, int CANID){
		int sn = 0;
		sendBuf.frame.clear();
		
		//���ݱ��ĵ����ݳ���, ���㱨�ĵ�֡��
		if(al.size() <= 7){
			sn = 1;	/*����<=7, ���õ�֡����*/
		}else{
			sn = (al.size()-6)/7;	/* ��һ֡�����ݳ���Ϊ6���ֽڣ���������֡�����ݳ���Ϊ7���ֽ� */
			if((al.size()-6)%7 == 0){
				sn = sn + 1;
			}else{
				sn = sn + 2;
			}
		}

		for (int i = 0; i < sn; i++) {	/* ���ȸ������ݳ���(֡��)��� CANFrameBuffer */
			sendBuf.frame.add(new Item());
		}
		
		/* �������,ÿ�����ĵ����ݳ���Ϊ:1~4095���Ϊ4095���ֽڣ� */
		if(sn == 1){ /* single frame */
			sendBuf.getFrame().get(0).data[3] = (byte)al.size(); //��䡰���ݳ����ֽڡ�
			for(byte i=0; i<al.size(); i++ ){
				sendBuf.getFrame().get(0).data[i+4] = al.get(i).byteValue(); /*byteValue():��Integer����ǿ��ת����byte��ֻȡ�Ͱ�λ����λ��Ҫ*/
			}
		}else{   /* multiple frame */
			sendBuf.getFrame().get(0).data[3] = (byte)(0x10+(al.size()>>8));  /* first frame, ǰ�����ֽڴ���֡���ͺ����ݳ���*/
			sendBuf.getFrame().get(0).data[4] = (byte)al.size();
			for(byte i = 0;i < 6; i++){
				sendBuf.getFrame().get(0).data[i+5] = al.get(i).byteValue();
			}
			
			int CANConFrameNum = (al.size()-6)/7; /* ����ʣ��������ռ�ģ�������֡�� */
			/*�������֡����Ŵ�1��ʼ��������F��ص�0��Ȼ���0��Fѭ������*/
			if(sn <= 16){ /* ��һ֡�����1-->���F����16֡���� (û��ѭ������)*/
				int index = 6;	/* 6������ڵ�һ֡��offset */
				/* snCount:����֡�����, 0x21~0x2F, 0x20~0x2F */
				byte snCount = 0x21;	/*0x21:2ΪN_PCITYPE(����֡),1Ϊ��� ,���ͺ�����������֡���ݵĵ�һ���ֽ�*/
				for(int j = 1; j <= CANConFrameNum; j++){
					for(byte m = 0;m < 7;m++){
						sendBuf.getFrame().get(j).data[m+4] = al.get(index++).byteValue();
					}
					sendBuf.getFrame().get(j).data[3] = snCount++;
				}
				if((al.size()-6)%7 == 0){
					//�պ�������֡
				}else{
					sendBuf.getFrame().get(CANConFrameNum+1).data[3] = snCount;
					for(int m = 0; m < (al.size()-6)%7; m++){
						sendBuf.getFrame().get(CANConFrameNum+1).data[m+4] = al.get(index++).byteValue();
					}
				}
			}else{  /*ѭ������)*/
				int index = 6;
				byte snCount = 0x21;
				for(int j = 1;j < 16;j++){ /*��Ŵ�1��F*/
					for(int m = 0;m < 7;m++){
						sendBuf.getFrame().get(j).data[m+4] = al.get(index++).byteValue();
					}
					sendBuf.getFrame().get(j).data[3] = snCount++;
				}
				/*��ŵ�����F��,��0��ʼѭ������*/
				snCount = 0x20;
				for(int j = 1;j <= sn-16;j++){	/*sn-16:ʣ�µ�֡����ǰ��ѭ��һ�ָպ�16֡��*/
					if(j != sn-16){
						for(int m = 0;m < 7;m++){
							sendBuf.getFrame().get(j+15).data[m+4] = al.get(index++).byteValue();
						}
					}else{ /* ���һ֡���� */
						/*���һ֡���ݵĳ��� = �ܳ���-(��һ��ѭ��������)-(�м�ѭ��������), al.size()-(6+15*7)-(j-1)*7 ==> al.size()-(6+(j+14)*7) */
						for(int m = 0;m < al.size()-(6+(j+14)*7);m++){
							sendBuf.getFrame().get(j+15).data[m+4] = al.get(index++).byteValue();
						}
					}
					sendBuf.getFrame().get(j+15).data[3] = snCount++;
					if(snCount == 0x30){	//sn:2F-->20,ѭ������
						snCount = 0x20;
					}
				}
			}
		}
		
		/* ���֡ͷ��checksum(����λ������ͬһ�����ݸ�ʽ),��ʽΪ��0xee | CANID(H) | CANID(H) | N_PCI | checksum
		 * ����N_PCIΪ�����Э�������Ϣ(Network layer protocol control information),����:��֡����һ֡������֡������֡  */
		for(int i = 0;i < sn;i++){
			sendBuf.getFrame().get(i).data[0] = (byte)0xee;
			sendBuf.getFrame().get(i).data[1] = (byte)(CANID>>8);	/* ȡCAN ID�ĸ߰�λ�����ڱ�׼֡��CAN IDΪ12λ��������16λ����ʾ�� */
			sendBuf.getFrame().get(i).data[2] = (byte)CANID;		/* ȡCAN ID�ĵͰ�λ */
			sendBuf.getFrame().get(i).data[11] = (byte)this.CheckSum(sendBuf.getFrame().get(i).data);
		}
	}
	
	/*----------У��ͼ���------------*/
	protected int CheckSum(byte[] buffer){	/* �ۼ���ͣ��������һ�������⣩ */
		int checkSum = 0;
		for(int i = 0;i < buffer.length-2;i++){
			checkSum += buffer[i];
		}
		return checkSum;
	}
	
	/*---------------ISO15765Э��Ľ���㷨(�����-->Ӧ�ò�)------------------*/
	public ArrayList<Byte> UnPackCANFrameData(CANFrameBuffer receiveBuf){
		int dataLength = 0;
		ArrayList<Byte> receiveData = new ArrayList<Byte>();
		if((receiveBuf.getFrame().get(0).data[3]&0xf0) == 0x10){	//��һ֡ first frame
			//��ȡ�������ĵĳ���
			dataLength = ((receiveBuf.getFrame().get(0).data[3]&0x0f)<<8)+receiveBuf.getFrame().get(0).data[4];	/* ��һ֡�У���һ���ֽڵĵ�4λ����2���ֽڣ�������ݳ��� */
			
			for (int i = 0; i < dataLength; i++) {/* ���ȸ������ݳ���(֡��)��� ArrayList */
				receiveData.add((byte) 0);
			}
			
			//sn ��ʾ֡������
			int sn = (dataLength-6)/7; /* ����֡���� */
			if((dataLength-6)%7 == 0){
				sn = sn + 1;
			}else{
				sn = sn + 2;
			}
			
			/*---------����ѭ�������ж��Ƿ�֡-----------*/
			for(int j = 2;j < sn;j++){
				//����ÿ�η�������֡, "sn��ֵ����1"��������Ϊ��������
				/*��һ֡û����ţ�������������֡��Ŵ�2��ʼ����������j��ʼֵΪ2�� Ҳ���Ǽ����3֡�������2֡���ĵ����֮�� */
				int tempData = receiveBuf.getFrame().get(j).data[3] - receiveBuf.getFrame().get(j-1).data[3];
				if(tempData == 1){
					/* ������ż��Ϊ1��������� */
				}else if(tempData == -15){
					//sn������F֮��, ���´�0��ʼ����(Ҳ���Ǵ�0~Fѭ������,���Ե�һ���ֽڵ�ֵ��ӦΪ20~2F)
					if((receiveBuf.getFrame().get(j).data[3] == 0x20)&&(receiveBuf.getFrame().get(j-1).data[3] == 0x2f)){
						
					}else{
						return null;
					}
				}else{
					return null;
				}
			}
			
			for(int k = 0;k < 6;k++){	/* ��һ֡�����ͺ����ݳ���ռǰ����2�ֽڣ�����ռ��6���ֽڣ�������ȡ��һ֡�е����� */
				receiveData.set(k, (byte)receiveBuf.getFrame().get(0).data[k+5]);
			}
			
			if(sn > 2){ /* sn>2(��֡����ʱsn>=2),��ʾ�ж�������֡ */
				for(int k = 0;k < sn - 2;k++){
					for(int m = 0;m < 7;m++){
						/* ����֡������(sn)��1��ʼ����һ֡������Ϊ0��,��Ϊk��0��ʼ��������������ʹ��k+1 */
						receiveData.set(m+k*7+6, (byte)receiveBuf.getFrame().get(k+1).data[m+4]);	/* ���Զ�������ݸ�ʽ�У�����֡�����ݴӵ�4���ֽڿ�ʼ����3���ֽ�Ϊ���ͺ���ţ������������offsetΪ4(m+4) */
					}
				}
				for(int k = 0;k < dataLength-(6+7*(sn-2));k++){ /*���һ������֡ ,ʣ���ֽ��� = �ܳ���-��һ֡����-ǰ�����е�����֡����    ==> dataLength-6-(sn-2)*7 */
					receiveData.set(k+(sn-2)*7+6, (byte)receiveBuf.getFrame().get(sn-1).data[k+4]);	/* �ܹ�sn֡���ģ����һ֡������Ϊsn-1��0��sn-1�� */
				}
			}else{  /* sn=2(��֡����ʱsn>=2),��ʾֻ��һ������֡ */
				for(int k = 0;k < dataLength-6;k++){
					receiveData.set(k+6, (byte)receiveBuf.getFrame().get(1).data[k+4]);
					
				}
			}
		}else{//single frame
			dataLength = receiveBuf.getFrame().get(0).data[3];
			for(int i = 0;i < dataLength;i++){
				receiveData.set(i, (byte)receiveBuf.getFrame().get(0).data[i+4]);
			}
		}
		return receiveData;
	}
	
	/*-----------���յ����ݺ����֡���ͽ��д���-----------*/
	
	/**
	 * @param data ֡�����ֽ�
	 * @param receiveBuffer ���ձ��Ļ��������洢һ֡���ģ�12���ֽڣ�
	 * @param id Ŀ��ECU�ڵ��ַ
	 */
	public int ReceiveNetworkFrameHandle(byte[] receiveBuffer, int id){
		ISO15765FrameType frameType;
		byte type = (byte) (receiveBuffer[3]&0xf0);
		int length = 0;
		//boolean result = true;	/* �ó�ʼֵ�д�ȷ�� */
		switch(type){//���ݲ�ͬ�Ĵ��䷽ʽ(֡��ʽ)���д���
			case 0x00:
				frameType = ISO15765FrameType.SINGLE_FRAME;//��֡
					break;
			case 0x10:
				frameType = ISO15765FrameType.FIRST_FRAME;//��һ֡
					break;
			case 0x20:
				frameType = ISO15765FrameType.CONSECUTIVE_FRAME;//����֡
					break;
			case 0x30:
				frameType = ISO15765FrameType.FLOW_CONTROL_FRAME;//������֡
					break;
			default:
				frameType = ISO15765FrameType.INVALID_FRAME;//��Ч֡
					break;
		}
		
		switch(frameType){
			case SINGLE_FRAME:
				for(int i = 0;i < 12;i++){
					this.frameBuffer.getFrame().get(0).data[i] = receiveBuffer[i];
				}
				length = receiveBuffer[3];
				break;
				
			case FLOW_CONTROL_FRAME:
					for(int i = 0;i < 12;i++){
						this.frameBuffer.getFrame().get(0).data[i] = receiveBuffer[i];
					}
					break;
					
			case FIRST_FRAME:	/* ���յ���һ֡,��Ҫ����������֡ */
					length = ((receiveBuffer[3]&0x0f)<<8)+receiveBuffer[4];	/* ��ȡ���ģ�����㣩�����ݳ��� */
					//System.out.println("Total receive CAN message length = "+length);
					Item item = new Item();
					item.data = SendFlowControlFrame(id);
					this.SendMessageToDevice(item.data);
					break;
					
			case CONSECUTIVE_FRAME:
					break;
					
			case INVALID_FRAME:
					break;
		}
		return length;
	}
	
	/*----------��������֡---------*/
	public byte[] SendFlowControlFrame(int CANID){
		byte[] sendBuffer = new byte[12];
		sendBuffer[0] = (byte)0xee;
		sendBuffer[1] = (byte)(CANID>>8);
		sendBuffer[2] = (byte)CANID;
		sendBuffer[3] = (byte)0x30;	/* 0x30:����3��ʾ����֡�� 0��ʾ��״̬���������ͣ� */
		sendBuffer[4] = (byte)0x0;	/* BS = 0 */
		sendBuffer[5] = (byte)0x01;	/* STmin = 1ms */
		sendBuffer[11] = (byte)CheckSum(sendBuffer);
		return sendBuffer;
	}
	
	/*------�����ݷ��͸�����ת�Ӱ�(��λ��)---------*/
	public void SendMessageToDevice(byte[] buf){
		try{
			outStream = socket.getOutputStream();
			outStream.write(buf);
		}catch(IOException i){
			i.printStackTrace();
		}
	}
	
	protected class ReceiveThread extends Thread{
		BluetoothSocket socket;
		InputStream inStream;
		CANFrameBuffer buf;
		
		public ReceiveThread(BluetoothSocket socket) {
			super();
			// TODO Auto-generated constructor stub
			this.socket = socket;
			try{
				inStream = socket.getInputStream();
			}catch(IOException e){
				e.printStackTrace();
			}
		}

		@Override
		public synchronized void start() {
			// TODO Auto-generated method stub
			super.start();
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			super.run();
			byte[] tempBuffer = new byte[12];
			int num = 0;
			buf = new CANFrameBuffer();
			int id = 0;
			
			while(id == response_can_id){
				try{
					num += inStream.read(tempBuffer); //ÿ�ζ�ȡ���12���ֽ�, ����ʵ�ʶ�ȡ�����ֽ��� 
					if(num < 12)
						id = (int)((tempBuffer[1]<<8)|tempBuffer[2]);
				}catch(IOException e){
					e.printStackTrace();
				}
			}
			 
			int length = ReceiveNetworkFrameHandle(tempBuffer, request_can_id);
			Item item = new Item();
			item.data = tempBuffer.clone();
			buf.getFrame().add(item);
			
			if(length <= 7){
				//single frame
			}else {
				//���ݱ��ĵ����ݳ���, ���㱨�ĵ�֡��
				int sn = (length-6)/7;	/* ��һ֡�����ݳ���Ϊ6���ֽڣ���������֡�����ݳ���Ϊ7���ֽ� */
				if((length-6)%7 == 0){
					sn = sn + 1;
				}else{
					sn = sn + 2;
				}
				for(int i=0; i<sn; i++){
					if(id == response_can_id){	//������֡���ܴ���������ݻ�����
						try {
							inStream.read(tempBuffer);
							Item it = new Item();
							it.data = tempBuffer.clone();
							buf.getFrame().add(it);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			if(tempBuffer[3] == (byte)0x30) //���յ�����֡
				this.notify();	//���������߳�
			
			receiveData = UnPackCANFrameData(buf);	//���ؽ��յ�������
		}
	}
	
	
	public class SendThread extends Thread{
		BluetoothSocket socket;
		OutputStream outStream;
		ArrayList<Byte> receiveData;
		CANFrameBuffer buf;
		
		public SendThread(BluetoothSocket socket, ArrayList<Byte> output) {
			super();
			// TODO Auto-generated constructor stub
			this.socket = socket;
			sendData = output;
			/*
			try{
				outStream = socket.getOutputStream();
			}catch(IOException e){
				e.printStackTrace();
			}
			*/
		}

		@Override
		public synchronized void start() {
			// TODO Auto-generated method stub
			super.start();
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			super.run();
			
			buf = new CANFrameBuffer();
			PackCANFrameData(sendData, buf, request_can_id);
			
			//byte[] tempBuffer = new byte[12];
			int num = buf.getFrame().size();
			/*
			try{
				outStream.write(buf.getFrame().get(0).data);	// ���͵�һ֡/��֡
				
				ReceiveThread receiveThread = new ReceiveThread(socket);
				receiveThread.start();
				
				if(num > 1){	//���ڶ�֡����,�����ڵ�һ֮֡����Ҫ�ȴ�����������֡
					wait();
					for(int i=1; i<=num-1; i++)
						outStream.write(buf.getFrame().get(i).data);
				}

			}catch(IOException | InterruptedException e){
				e.printStackTrace();
			}
			*/
			for(int j=0; j<num; j++){
				for(int i=0; i<12; i++){
					int a = (int)(buf.getFrame().get(j).data[i]&0xFF);
					System.out.printf("%2h ", a);
				}
				System.out.println();
			}
		}
	}
}