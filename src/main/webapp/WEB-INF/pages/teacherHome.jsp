<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%-- Home page for Teachers --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
    <head>
        <title>Teach Children to Save - Teacher Home</title>
        <%@include file="include/commonHead.jsp"%>
    </head>
    <body id="teacherHome" class="teacherHome">

        <a href="#main" class="ada-read">Skip to main content</a>

        <div class="decor"></div>

        <%@include file="include/header.jsp" %>

        <div class="mainCnt">

        <%@include file="include/navigation.jsp" %>

            <main id="main">   

		        <h1>
		        	Your Home Page
		        </h1>
		        
	            <h2>Actions</h2>

		            <ul class="noUl">
		                <li class="mb1">
							<button onclick="js.loadURL('createEvent.htm');" class="editOrRegister">
								Create new class
							</button>
		                </li>
		                <li class="mb1">
							<button onclick="js.loadURL('editPersonalData.htm');" class="editOrRegister">
								Edit my Account
							</button>
		                </li>
		            </ul>

		        <div id="events">
		            <h2>My Classes</h2>
		            <table id="eventTable">
		                <thead>
		                    <tr>
		                        <th scope="col">Date</th>
		                        <th scope="col">Time</th>
		                        <th scope="col" class="center">Grade</th>
		                        <th scope="col" class="center">Students</th>
		                        <th scope="col">Volunteer</th>
		                        <th scope="col">Bank</th>
                                <th scope="col">
									<span class="ada-read">Column of Delete buttons</span>
								</th>
		                    </tr>
		                </thead>
		                <tbody>
		                    <c:forEach var="event" items="${events}">
		                        <tr>
		                            <td><c:out value="${event.eventDate.pretty}"/></td>
		                            <td><c:out value="${event.eventTime}"/></td>
		                            <td class="center"><c:out value="${event.grade}"/></td>
		                            <td class="center"><c:out value="${event.numberStudents}"/></td>
		                            <td>
		                                <c:out value="${event.linkedVolunteer.firstName}" default="no volunteer"/>
		                                <c:out value="${event.linkedVolunteer.lastName}" default=""/>
		                            </td>
		                            <td><c:out value="${event.linkedVolunteer.linkedBank.bankName}" default=""/></td>
                                    <td>
										<button onclick="js.loadURL('teacherCancel.htm?eventId=<c:out value="${event.eventId}"/>');" class="editOrRegister delete">
											Delete
										</button>
										<%--<a href="teacherCancel.htm?eventId=<c:out value="${event.eventId}"/>">cancel</a>--%>
									</td>
		                        </tr>
		                    </c:forEach>
		                </tbody>
		            </table>
		        </div>

			</main>

		</div><%-- mainCnt --%>			        
		       
        <%@include file="include/footer.jsp"%>

    </body>
</html>