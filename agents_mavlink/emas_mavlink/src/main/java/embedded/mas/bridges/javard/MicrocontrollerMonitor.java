
package embedded.mas.bridges.javard;

import embedded.mas.bridges.jacamo.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonParsingException;

import embedded.mas.exception.PerceivingException;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;
/**
 * Esta classe ilustra um "produtor" em um exemplo de produtor-consumidor.
 * O produtor, nesse caso, alimenta uma lista com números inteiros em sequência.
 * 
 * 
 * 
 * @author maiquel
 *
 */


public class MicrocontrollerMonitor extends Thread {

	// Changed from the original list-only design so the monitor can push the newest beliefs
	// directly into JSONWatcherDevice without growing an intermediate queue.
	private JSONWatcherDevice device;
	private List<Collection<Literal>> lista;
	private IPhysicalInterface microcontroller;
	
	// New constructor for the direct device-update path used by the current JSON watcher.
	public MicrocontrollerMonitor(JSONWatcherDevice device, IPhysicalInterface microcontroller) {
		super();
		this.device = device;
		this.microcontroller = microcontroller;
	}

	public MicrocontrollerMonitor(List<Collection<Literal>> lista, IPhysicalInterface microcontroller) {
		super();
		this.lista = lista;
		this.microcontroller = microcontroller;
	}

	@Override
	public void run(){
		while(true) {
			try {
				boolean consumed = this.decode();
				if(!consumed) {
					// Changed from the old random sleep: when there is no new data, back off briefly
					// so the polling loop stays responsive without adding long random delays.
					try {
						Thread.sleep(5);
					} catch (InterruptedException e2) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			} catch (PerceivingException e1) {
				if(e1.getMessage()!=null) System.err.println(e1.getMessage());
				e1.printStackTrace();
			}
		}
	}
	
	
public boolean decode() throws PerceivingException {
		String json = microcontroller.read();
		if(json==null || json.equals("")) {
			// Changed from the old void method so run() can detect "no data" and sleep only then.
			return false;
		}
		if(json.equals("Message conversation error")) { //if the message is not propealy read
			throw new PerceivingException();		
		}
		else{
			ArrayList<Literal> percepts = new ArrayList<Literal>(); //adicionar os valores lidos arduino na lista percepts (dúvidas - olhar DemoDevice)
			JsonReader reader = Json.createReader(new ByteArrayInputStream(json.getBytes()));			
			JsonObject jsonObject;
			try {
				jsonObject = reader.readObject();
			} catch (JsonParsingException e) {
				throw new PerceivingException("Invalid JSON: " + json);	
			}
			for(String key: jsonObject.keySet()) { //iterar sobre todos os elementos do JsonObject - a variável "key" armazena cada chave do objeto json    		
				Object value = jsonObject.get(key); //obtém o valor associado à chave "key"
				String belief = key +"(";
				if(!(value instanceof JsonArray)) //se o valor não for um vetor (ou seja, se for uma belief com apenas um valor)
					belief = belief + value;
				else { //se for um vetor [v1,v2,...,vn], monta uma belief key(v1,v2,...,vn)    			
					belief = belief + value.toString().replace("[","").replace("]", "");	 	
				}
				belief = belief + ")";

				//System.out.println(belief);
				percepts.add(Literal.parseLiteral(belief));
			}
			if(this.device != null) {
				// New fast path: keep only the newest percept set in the device.
				this.device.updateLatestBeliefs(percepts);
			} else if (this.lista != null) {
				// Keep the original list-based behavior for older call sites.
				this.lista.add(percepts);
			}
			return true;
		}
	}

}
