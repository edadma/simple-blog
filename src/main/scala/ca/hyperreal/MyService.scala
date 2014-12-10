package ca.hyperreal

import java.util.Date
import java.text.SimpleDateFormat

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import com.mongodb.casbah.Imports._
import com.mongodb.BasicDBObject
import scala.collection.JavaConversions._


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(route)
}

// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {
	val PORT = "8080"
	val TITLE = "A Blog Service"
	val DATE_FORMAT = new SimpleDateFormat( "MMMM d, yyyy h:mm a" )
	val mongoClient = MongoClient( "localhost", 27017 )
	val db = mongoClient( "app" )
	val users = db( "users" )
	val route =
		pathPrefix("assets") {
			get {
				getFromResourceDirectory("public")
			}
		} ~
		pathPrefix("webjars") {
			get {
				getFromResourceDirectory("META-INF/resources/webjars")
			}
		} ~
		host( Boot.interfaceName ) {
			path( "about" ) {
				val about = <h2>Simple blog service demonstrating Spray with MongoDB</h2>
				
				optionalCookie( "credentials" ) {
					case Some( _ ) =>
						complete {
							page( TITLE, loggedin = true, 'About )( about )
						}
					case None =>
						complete {
							page( TITLE, loggedin = false, 'About )( about )
						}
				}
			} ~
			path( "contact" ) {
				val contact = <h2>No interest in being contacted</h2>
				
				optionalCookie( "credentials" ) {
					case Some( _ ) =>
						complete {
							page( TITLE, loggedin = true, 'Contact )( contact )
						}
					case None =>
						complete {
							page( TITLE, loggedin = false, 'Contact )( contact )
						}
				}
			} ~
			path( "logout" ) {
				get {
					optionalCookie( "credentials" ) {
						case Some(credentials) =>
							deleteCookie( "credentials" ) {
								respondWithHeader( HttpHeaders.`Cache-Control`(CacheDirectives.`no-cache`) ) {
									redirect( "/", StatusCodes.PermanentRedirect )
								}
							}
						case None =>
							redirect( "/", StatusCodes.PermanentRedirect )
					}
				}
			} ~
			path("register") {
				get {
					optionalCookie( "credentials" ) {
						case Some( _ ) =>
							redirect( "/", StatusCodes.PermanentRedirect )
						case None =>
							complete {
								page( title = TITLE, loggedin = false, 'Register )
								{
									<form action="register" method="POST">
										<h1>Register</h1>
										<p><input type="text" name="username" value="" placeholder="Username"/></p>
										<p><input type="password" name="password" value="" placeholder="Password"/></p>
										<p><input type="text" name="name" value="" placeholder="Real name"/></p>
										<p><input type="text" name="title" value="" placeholder="Blog title"/></p>
										<p><input type="text" name="subtitle" value="" placeholder="Blog subtitle"/></p>
										<p class="submit"><input type="submit" value="Register"/></p>
									</form>
								}
							}
					}
				} ~
				post {
					formFields('username, 'password, 'name, 'title, 'subtitle) { (username, password, name, title, subtitle) =>
						users.findOne( MongoDBObject("username" -> username) ) match
						{
							case Some( _ ) =>
							case None =>
								users.insert( MongoDBObject("username" -> username, "password" -> password, "realname" -> name, "title" -> title, "subtitle" -> subtitle,
									"posts" -> Nil) )
						}
						
						redirect( "/", StatusCodes.SeeOther )
					}
				}
			} ~
			path( "post" ) {
				post {
					formFields('headline, 'text, 'username) { (headline, text, username) =>
						users.update( MongoDBObject("username" -> username), $push("posts" -> MongoDBObject("$each" -> List(
							MongoDBObject("headline" -> headline, "post" -> text, "date" -> new Date)),
							"$position" -> 0)) )
						redirect( Uri("http://" + username + "." + Boot.interfaceName + ":" + PORT), StatusCodes.SeeOther )
					}
				}
			} ~
			path("") {
				get {
					optionalCookie( "credentials" ) {
						case Some(credentials) =>
							val Array(username, password) = credentials.content.split( ":", 2 )
							
							if (users.findOne( MongoDBObject("username" -> username, "password" -> password) ) != None)
								complete
								{
								val realname = users.findOne( MongoDBObject("username" -> username) ).get("realname")
								
									page( title = username + " - " + TITLE, loggedin = true, 'Home )
									{
										<form action="post" method="POST" id="post">
											<h1>Welcome <a href={"http://" + username + "." + Boot.interfaceName + ":" + PORT}>{realname}</a></h1>
											<p><input type="text" name="headline" placeholder="Headline"/></p>
											<p><textarea rows="4" cols="50" name="text" placeholder="Blog post text" form="post"></textarea></p>
											<input type="hidden" name="username" value={username}/>
											<p class="submit"><input type="submit" value="Post"/></p>
										</form>
									}
								}
							else
								deleteCookie( "username" ) {
									redirect( "logout", StatusCodes.PermanentRedirect )
								}
						case None =>
							optionalCookie( "username" ) { username =>
								complete {
									page( title = TITLE, loggedin = false, 'Home )
									{
										<form action="login" method="POST">
											<h2>Login</h2>
											<p><input type="text" name="username" value={if (username != None) username.get.content else ""} placeholder="Username"/></p>
											<p><input type="password" name="password" value="" placeholder="Password"/></p>
											<p class="remember_me">
											<label>
												{if (username != None)
													<input type="checkbox" name="remember_me" checked="checked"/>
												else
													<input type="checkbox" name="remember_me"/>}
												Remember me on this computer
											</label>
											</p>
											<p class="submit"><input type="submit" value="Login"/></p>
										</form>
									}
								}
							}
					}
				}
			} ~
			path( "login" )
			{
				post {
					formFields('username, 'password, 'remember_me ? "off") { (username, password, remember_me) =>
						users.findOne( MongoDBObject("username" -> username, "password" -> password) ) match
						{
							case Some( _ ) =>
								val credentials = HttpCookie( "credentials", content = username + ':' + password )
								
								if (remember_me == "on")
									setCookie( credentials, HttpCookie("username", username) ) {
										redirect( "/", StatusCodes.SeeOther )
									}
								else
									deleteCookie( "username" ) {
										setCookie( credentials ) {
											redirect( "/", StatusCodes.SeeOther )
										}
									}
							case None =>
								redirect( "/", StatusCodes.SeeOther )
						}
					}
				}
			}
		} ~
		host( ("[^.]*[.]" + "\\Q" + Boot.interfaceName + "\\E")r ) { host =>
			val Array(subdomain, domain) = host.split( "[.]", 2 )
			
			users.findOne( MongoDBObject("username" -> subdomain) ) match
			{
				case Some( blog ) =>
					respondWithMediaType( `text/html` ) {
						complete {
							<html>
								<head>
									<meta charset="utf-8"/>
									<meta http-equiv="X-UA-Compatible" content="IE=edge"/>
									<meta name="viewport" content="width=device-width, initial-scale=1"/>
									
									<title>{blog("title")}</title>

									<link href="webjars/bootstrap/3.3.1/css/bootstrap.min.css" rel="stylesheet"/>
									<link href="assets/css/blog.css" rel="stylesheet"/>
								</head>
								
								<body>
									<div class="container">

										<div class="blog-header">
											<h1 class="blog-title">{blog("title")}</h1>
											<p class="lead blog-description">{blog("subtitle")}</p>
										</div>

										<div class="row">
											<div class="col-sm-8 blog-main">
												{for (e <- blog("posts").asInstanceOf[java.util.List[BasicDBObject]])
													yield
													{
														<div class="blog-post">
															<h2 class="blog-post-title">{e.get("headline")}</h2>
															<p class="blog-post-meta">{DATE_FORMAT.format( e.get("date") )} by <a href="#">{blog("realname")}</a></p>
															<p>{e.get("post")}</p>
														</div>
													}
												}
											</div>
										</div>
									</div>

									<footer class="blog-footer">
										<p>Powered by <a href={"http://" + Boot.interfaceName + ":" + PORT}>{TITLE}</a></p>
										<p><a href="#">Back to top</a></p>
									</footer>
								</body>
							</html>
						}
					}
				case None =>
					redirect( Uri("http://" + domain + ":" + PORT), StatusCodes.PermanentRedirect )
			}
		}

	def page( title: String, loggedin: Boolean, activePage: Symbol )( content: xml.Elem ) =
	{
		def active( link: Symbol, item: xml.Elem ) =
		{
			if (link == activePage)
				<li class="active">{item}</li>
			else
				<li>{item}</li>
		}
		
		<html>
			<head>
				<meta charset="utf-8"/>
				<meta http-equiv="X-UA-Compatible" content="IE=edge"/>
				<meta name="viewport" content="width=device-width, initial-scale=1"/>
				
				<title>{title}</title>

				<link href="webjars/bootstrap/3.3.1/css/bootstrap.min.css" rel="stylesheet"/>
				<link href="assets/css/main.css" rel="stylesheet"/>
			</head>
			
			<body>
				<nav class="navbar navbar-inverse navbar-fixed-top" role="navigation">
				<div class="container">
					<div class="navbar-header">
					<button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
						<span class="sr-only">Toggle navigation</span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
					</button>
					<a class="navbar-brand" href="/">{TITLE}</a>
					</div>
					<div id="navbar" class="collapse navbar-collapse">
					<ul class="nav navbar-nav">
						{active( 'Home, <a href="/">Home</a> )}
						{active( 'About, <a href="about">About</a> )}
						{active( 'Contact, <a href="contact">Contact</a> )}
						{if (loggedin) <li><a href="logout">Logout</a></li> else active( 'Register, <a href="register">Register</a> )}
					</ul>
					</div><!--/.nav-collapse -->
				</div>
				</nav>

				<div class="container">
					<div class="row">
						<div class="col-sm-8">
							{content}
						</div>
					</div>
				</div><!-- /.container -->

				<script src="webjars/jquery/1.11.1/jquery.min.js"></script>
				<script src="webjars/bootstrap/3.3.1/js/bootstrap.min.js"></script>
			</body>
		</html>
	}
}