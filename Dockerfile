from mozilla/sbt 

COPY . /usr/music-quiz
WORKDIR /usr/music-quiz

RUN sbt assembly

CMD java -Xmx512M -cp /usr/music-quiz/target/scala-2.13/music-quiz-assembly-0.1.0-SNAPSHOT.jar dev.ostrander.musicquiz.MusicQuiz ${DISCORD_TOKEN}
