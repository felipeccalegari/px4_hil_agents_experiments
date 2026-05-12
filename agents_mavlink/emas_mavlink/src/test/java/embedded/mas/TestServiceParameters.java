package embedded.mas;

import static org.junit.Assert.*;

import org.junit.Test;

import embedded.mas.bridges.ros.ServiceArrayParam;
import embedded.mas.bridges.ros.ServiceParam;
import embedded.mas.bridges.ros.ServiceParameters;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.NumberTermImpl;

public class TestServiceParameters {

	@Test
	public void testToJson() {
		ServiceParameters p = new ServiceParameters();		
		//integer param
		p.add(new ServiceParam("p1", 1));

		//string param
		p.add(new ServiceParam("p2", "test"));
		
		//array param
		p.addParameter("arrayOfFloatParameter", new Float[]{Float.parseFloat("3.14"), Float.parseFloat("1.99")} );


		//nested params
		ServiceParameters nestingParam = new ServiceParameters();
		ServiceParam nestedParam1 = new ServiceParam("nested1", 888);
		ServiceParam nestedParam2 = new ServiceParam("nested2", 999);
		nestingParam.add(nestedParam1);
		nestingParam.add(nestedParam2);
		p.add(new ServiceParam("nestedP", nestingParam)); //TODO: check: the service typing is useless here

		System.out.println(p.toJson());
		System.out.println("{\"p1\":1,\"p2\":\"test\",\"arrayOfFloatParameter\":[3.14,1.99],\"nestedP\":{\"nested1\":888,\"nested2\":999}}");

		System.out.println(p.toJson().toString().equals("{\"p1\":1,\"p2\":\"test\",\"arrayOfFloatParameter\":[3.14,1.99],\"nestedP\":{\"nested1\":888,\"nested2\":999}}"));

		assertTrue("Fail to convert param to json", p.toJson().toString().equals("{\"p1\":1,\"p2\":\"test\",\"arrayOfFloatParameter\":[3.14,1.99],\"nestedP\":{\"nested1\":888,\"nested2\":999}}"));






	}

	
	@Test
	public void testSetValues() {
		//create the following list of parameters: [[1,2,3],123,[11,22,33]]
		ListTermImpl list = new ListTermImpl();
		ListTermImpl nestedList1 = new ListTermImpl();
		nestedList1.add(new NumberTermImpl(1));
		nestedList1.add(new NumberTermImpl(2));
		nestedList1.add(new NumberTermImpl(3));		
		ListTermImpl nestedList2 = new ListTermImpl();
		nestedList2.add(new NumberTermImpl(11));
		nestedList2.add(new NumberTermImpl(22));
		nestedList2.add(new NumberTermImpl(33));
		
		
		ServiceParameters params = new ServiceParameters();
		ServiceParam p1 = new ServiceParam("linear", null);
		ServiceParameters pLinear = new ServiceParameters();
		ServiceParam xLinear = new ServiceParam("x", null);
		ServiceParam yLinear = new ServiceParam("y", null);
		ServiceParam zLinear = new ServiceParam("z", null);
		pLinear.add(xLinear); 
		pLinear.add(yLinear);
		pLinear.add(zLinear);
		p1.setParamValue(pLinear);
		ServiceParam p2 = new ServiceParam("angular", null);
		ServiceParameters pAngular = new ServiceParameters();
		ServiceParam xAngular = new ServiceParam("x", null);
		ServiceParam yAngular = new ServiceParam("y", null);
		ServiceParam zAngular = new ServiceParam("z", null);
		pAngular.add(xAngular);
		pAngular.add(yAngular);
		pAngular.add(zAngular);
		p2.setParamValue(pAngular);
	
		params.add(p1);
		
		params.add(new ServiceParam("test",null));
		assertFalse("if the value is a list, the corresponding param must be a list of parameters",params.setValues(list.toArray())); 
		
		
		params.add(new ServiceParam("test", null));
		
		params.remove(params.size()-1);
		params.add(p2);
		
		
		assertTrue(params.toJson().toString().equals("{\"linear\":{\"x\":null,\"y\":null,\"z\":null},\"test\":null,\"angular\":{\"x\":null,\"y\":null,\"z\":null}}"));	
		
		
		
		

		assertFalse("It should not accept array of params with different size of the list of service params",params.setValues(list.toArray())); 
		
		list.add(nestedList1);
		list.add(new NumberTermImpl(123));
		list.add(nestedList2);
		
		assertTrue("It must accept array of params with same size of the list of service params",params.setValues(list.toArray())); 		
		assertTrue(params.toJson().toString().equals("{\"linear\":{\"x\":1,\"y\":2,\"z\":3},\"test\":123,\"angular\":{\"x\":11,\"y\":22,\"z\":33}}"));
		
	}
	
	@Test
	public void testSetValuesArrayParam() {
		////create the following list of parameters: [0.1, [[1,2,3],[4,5,6]],0.2]
		ListTermImpl list = new ListTermImpl();
		
		ListTermImpl arrayParam = new ListTermImpl();
				
		ListTermImpl nestedList1 = new ListTermImpl();
		nestedList1.add(new NumberTermImpl(1));
		nestedList1.add(new NumberTermImpl(2));
		nestedList1.add(new NumberTermImpl(3));		
		ListTermImpl nestedList2 = new ListTermImpl();
		nestedList2.add(new NumberTermImpl(4));
		nestedList2.add(new NumberTermImpl(5));
		nestedList2.add(new NumberTermImpl(6));
		
		arrayParam.add(nestedList1);
		arrayParam.add(nestedList2);
		
		list.add(new NumberTermImpl(0.1));
		list.add(arrayParam);
		list.add(new NumberTermImpl(0.2));
		
		System.out.println("LIST:  " + list);
		
		ServiceParam y11 = new ServiceParam("y11", 1);
		ServiceParam y12 = new ServiceParam("y12", 2);
		ServiceParam y13 = new ServiceParam("y13", 3);
		ServiceParameters y1 = new ServiceParameters();
		y1.add(y11); y1.add(y12); y1.add(y13);
		
		ServiceParam y21 = new ServiceParam("y21", 1);
		ServiceParam y22 = new ServiceParam("y22", 2);
		ServiceParam y23 = new ServiceParam("y23", 3);
		ServiceParameters y2 = new ServiceParameters();
		y2.add(y21); y2.add(y22); y2.add(y23);
		
		ServiceParameters parametersY = new ServiceParameters();
		parametersY.add(new ServiceParam("par1", y1));
		parametersY.add(new ServiceParam("par2", y2));
		
		ServiceArrayParam y = new ServiceArrayParam("y", parametersY);
		
		ServiceParameters parameters = new ServiceParameters();
		parameters.add(new ServiceParam("x", 0.1));
		parameters.add(y);
		parameters.add(new ServiceParam("z", 0.2));
		
		System.out.println(">>>" + parameters.toJson());
		
		parameters.setValues(list.toArray());
		
		System.out.println("+++" + parameters.toJson());
		
		
	}
}
