<%-- Home page for Volunteers --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
    <head>
        <title>Teach Children to Save - Site Administrator Home</title>
        <%@include file="include/commonHead.jsp"%>
    </head>
    <body id="siteAdminHome" class="siteAdminHome">
    
        <a href="#main" class="ada-read">Skip to main content</a>

        <div class="decor"></div>

        <%@include file="include/header.jsp" %>

        <div class="mainCnt">

        <%@include file="include/navigation.jsp" %>

            <main id="main">    


		        <h1>
		        	Admin Home Page
		        </h1>
	        
	            <h2>Actions</h2>
	         
	            <ul class="noUl">
	                <li class="mb1">
	                	<button onclick="js.loadURL('bank/banks.htm');" class="editOrRegister">
	                		Add/Remove/Edit Banks
	                	</button>
                	</li>
                	
	                <li class="mb1">
	                	<button onclick="js.loadURL('school/schools.htm');" class="editOrRegister">
	                		Add/Remove/Edit Schools
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('teacher/teachers.htm');" class="editOrRegister">
	                		Remove/Edit Teachers
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('volunteer/volunteers.htm');" class="editOrRegister">
	                		Remove/Edit Volunteers
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('teacher/teachers.htm');" class="editOrRegister">
	                		List Teachers
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('volunteer/volunteers.htm');" class="editOrRegister">
	                		List Volunteers
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('class/classes.htm');" class="editOrRegister">
	                		List Classes
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('adminSendEmailAnnounce.htm');" class="editOrRegister">
	                		Send Email Announcement
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('adminEditAllowedDates.htm');" class="editOrRegister">
	                		Add/Remove Event Dates
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('adminEditAllowedTimes.htm');" class="editOrRegister">
	                		Add/Remove Event Times
	                	</button>
	                </li>
	                
	                <li class="mb1">
	                	<button onclick="js.loadURL('editPersonalData.htm');" class="editOrRegister">
	                		Edit my Data
	                	</button>
	                </li>
	            </ul>

		
			</main>

		</div><%-- mainCnt --%>	

        <%@include file="include/footer.jsp"%>

    </body>
</html>