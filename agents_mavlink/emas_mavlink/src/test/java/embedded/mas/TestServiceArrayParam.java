package embedded.mas;

import static org.junit.Assert.*;

import org.junit.Test;

import embedded.mas.bridges.ros.ServiceArrayParam;
import embedded.mas.bridges.ros.ServiceParam;
import embedded.mas.bridges.ros.ServiceParameters;

public class TestServiceArrayParam {

	
	@Test
	public void testToJsonValue_1() {
		ServiceParam p11 = new ServiceParam("p11", 1);
		ServiceParam p12 = new ServiceParam("p12", 2);
		ServiceParam p13 = new ServiceParam("p13", 3);
		ServiceParameters p1 = new ServiceParameters();
		p1.add(p11); p1.add(p12); p1.add(p13);
		
		ServiceArrayParam param = new ServiceArrayParam("param", p1);
		
		assertTrue(param.toJsonValue().toString().equals("\"param\":[1,2,3]"));
	}
	
	@Test
	public void testToJsonValue_2() {
		ServiceParam p11 = new ServiceParam("p11", 1);
		ServiceParam p12 = new ServiceParam("p12", 2);
		ServiceParam p13 = new ServiceParam("p13", 3);
		ServiceParameters p1 = new ServiceParameters();
		p1.add(p11); p1.add(p12); p1.add(p13);
		
		ServiceParam p21 = new ServiceParam("p21", 1);
		ServiceParam p22 = new ServiceParam("p22", 2);
		ServiceParam p23 = new ServiceParam("p23", 3);
		ServiceParameters p2 = new ServiceParameters();
		p2.add(p21); p2.add(p22); p2.add(p23);
		
		ServiceParameters parameters = new ServiceParameters();
		parameters.add(new ServiceParam("par1", p1));
		parameters.add(new ServiceParam("par2", p2));
		
		ServiceArrayParam param = new ServiceArrayParam("param", parameters);
		
		assertTrue(param.toJsonValue().toString().equals("\"param\":[{\"p11\":1,\"p12\":2,\"p13\":3},{\"p21\":1,\"p22\":2,\"p23\":3}]"));
	}

}
