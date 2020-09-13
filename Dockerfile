from openjdk:8

COPY music-quiz-assembly-0.1.0-SNAPSHOT.jar /usr

CMD java -Xmx512M -cp /usr/music-quiz-assembly-0.1.0-SNAPSHOT.jar dev.ostrander.musicquiz.MusicQuiz ${DISCORD_TOKEN}
