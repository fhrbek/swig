package cz.sml.swig.server;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LogServlet extends HttpServlet {

	private static final long serialVersionUID = 4212969850369176056L;

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		response.setContentType("text/plain");
		response.setCharacterEncoding("utf-8");

		try {
			response.getOutputStream().write(ImageScanner.getRecentLog().getBytes("utf-8"));
		}
		catch(Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

}
