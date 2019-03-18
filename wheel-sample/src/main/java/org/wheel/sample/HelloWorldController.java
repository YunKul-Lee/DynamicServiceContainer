package org.wheel.sample;

import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HelloWorldController {

	@RequestMapping(value = "/hello")
	public void hello(Locale locale, Model model, HttpServletRequest request, HttpServletResponse response) {

		try {
			BufferedWriter writer = new BufferedWriter(response.getWriter());
			writer.write("V12 " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
			writer.flush();
		}
		catch (Exception e) {
			// IGNORE
		}
	}

}
