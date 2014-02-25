<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

width: ${currentNode.properties.width.long}<br />
height: ${currentNode.properties.height.long}<br />
<c:url value="${url.files}${currentNode.properties.image.node.path}?t=blogimagescalingbydef" var="imageUrl"/>
<img src="${imageUrl}" />
