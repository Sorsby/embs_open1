package q2mote;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class Sink1 {

    private static Timer  tsend;
    private static Timer  tstart;
    private static Timer tled;
    private static Timer toff;
    private static boolean light=false;
    
    private static byte[] xmit;
    private static long   wait;
    static Radio radio = new Radio();
    private static int n = 8; // number of beacons of sync phase - sample only, assessment will use unknown values
    private static int nc;
    private static boolean receiving = false;
    
    private static int t = 600; // milliseconds between beacons - sample only, assessment will use unknown values 
    
    // settings for sink A
    private static byte channel = 0; // channel 11
    private static byte panid = 0x11;
    private static byte address = 0x11;

    static {
    	Assembly.setSystemInfoCallback(new SystemInfo(null){
			public int invoke(int type, int info){ 
				return onDelete(type, info);
			}
		});
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        
         
        // Set channel 
        radio.setChannel((byte)channel);
        
        
        // Set the PAN ID and the short address
        radio.setPanId(panid, true);
        radio.setShortAddr(address);


        // Prepare beacon frame with source and destination addressing
        xmit = new byte[12];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
        Util.set16le(xmit, 3, panid); // destination PAN address 
        Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
        Util.set16le(xmit, 7, panid); // own PAN address 
        Util.set16le(xmit, 9, address); // own short address 

        xmit[11] = (byte)n;

		// register delegate for received frames
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int info, long time) {
                    return  Sink1.onReceive(flags, data, len, info, time);
                }
            });
        
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+Time.toTickSpan(Time.SECONDS, 600));


        
        // Setup a periodic timer callback for beacon transmissions
        tsend = new Timer();
        tsend.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    Sink1.periodicSend(param, time);
                }
            });
            
            
        
        
        // Setup a periodic timer callback to restart the protocol
        tstart = new Timer();
        tstart.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    Sink1.restart(param, time);
                }
            });
        	
        
        tled = new Timer();
        tled.setCallback(new TimerEvent(null) {
        	public void invoke (byte param, long time) {
        		Sink1.LEDoff(param, time);
        	}
        });
        
        toff = new Timer();
        toff.setCallback(new TimerEvent(null) {
        	public void invoke (byte param, long time) {
        		Sink1.stopReceive(param, time);
        	}
        });
        
        
        // Convert the periodic delay from ms to platform ticks
        wait = Time.toTickSpan(Time.MILLISECS, t);
        
        
        tstart.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, 5)); //starts the protocol 5 seconds after constructing the assembly
        
        
        
    }

    // Called when a frame is received or at the end of a reception period 
    private static int onReceive (int flags, byte[] data, int len, int info, long time) {
        if (data == null) { // marks end of receiving
            // turn green LED off 
        	radio.startRx(Device.ASAP, 0, Time.currentTicks()+Time.toTickSpan(Time.SECONDS, 600));
	    	
            return 0;
        }
        if (receiving) {
        	LED.setState((byte)2, (byte)1);
            tled.setParam((byte)2);
         	tled.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, 1));
            Logger.appendString(csr.s2b("IN RECEPTION PERIOD: Sink 1 received beacon with payload "));
    		Logger.appendInt((int)data[11]);
    		Logger.appendString(csr.s2b(" at time "));
    		Logger.appendLong(Time.currentTime(Time.MILLISECS));
            Logger.flush(Mote.ERROR);
        } else {
        	LED.setState((byte)0, (byte)1);
            tled.setParam((byte)0);
         	tled.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, 1));
            Logger.appendString(csr.s2b("OUT OF RECEPTION PERIOD: Sink 1 received beacon with payload "));
    		Logger.appendInt((int)data[11]);
    		Logger.appendString(csr.s2b(" at time "));
    		Logger.appendLong(Time.currentTime(Time.MILLISECS));
            Logger.flush(Mote.ERROR);
        }
		
        return 0;
        
    }

 
    // Called on a timer alarm
    public static void periodicSend(byte param, long time) {
        
        if(nc>0){
	        // transmit a beacon 
    	    radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
        	// program new alarm
        	tsend.setAlarmBySpan(wait);
        	nc--;
        	xmit[11]--;
        }
        else{
        	//start reception phase
        	receiving = true;
	        Logger.appendString(csr.s2b("Sink 1 has begun reception at "));
	        Logger.appendLong(Time.currentTime(Time.MILLISECS));
	        Logger.flush(Mote.WARN);
	        toff.setAlarmBySpan(wait);
	        // turn green LED on 
	        LED.setState((byte)1, (byte)1);
	        
        }
        
    }


    // Called on a timer alarm, starts the protocol
    public static void restart(byte param, long time) {
        
        nc=n;
        xmit[11]=(byte)n;
       	tsend.setAlarmBySpan(0);
        
    }
    
    public static void stopReceive(byte param, long time) {
    	receiving = false;
    	LED.setState((byte)1, (byte)0);
        
        //set alarm to restart protocol
    	tstart.setAlarmBySpan(10*wait);
                
    	Logger.appendString(csr.s2b("Sink 1 has finished reception at "));
        Logger.appendLong(Time.currentTime(Time.MILLISECS));
        Logger.flush(Mote.WARN);
    	
    }
    
    public static void LEDoff(byte param, long time) {
    	LED.setState(param, (byte)0);
    }

	private static int onDelete(int type, int info){
		Logger.appendString(csr.s2b("Sink assembly deleted"));
		Logger.flush(Mote.INFO);
		if(type==Assembly.SYSEV_DELETED){
			radio.close();
		}
		return 0;
	}

}
