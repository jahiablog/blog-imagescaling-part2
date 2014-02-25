Jahia Image manipulation part 2
========

Example how to manipulate images within Jahia.
Fore more information please read the blogs at : **http://jahia.com**

Quick installation and deployment instructions
========
git clone https://github.com/jahiablog/blog-imagescaling-part2
mvn package

stop jahia
copy the resulting war from target/ in tomcat/webapps/ROOT/WEB-INF/var/shared_modules/
restart jahia


For developers
========
If you are planning to continuously develop modules for Jahia please read this section
of the documentation: **http://www.jahia.com/community/documentation/development/prerequisites.html**
This will help you develop Jahia modules more efficiently.