package cat.tecnocampus.tinder.persistence;

import cat.tecnocampus.tinder.application.exception.ProfileNotFound;
import cat.tecnocampus.tinder.domain.Profile;
import cat.tecnocampus.tinder.domain.Like;
import org.simpleflatmapper.jdbc.spring.JdbcTemplateMapperFactory;
import org.simpleflatmapper.jdbc.spring.ResultSetExtractorImpl;
import org.simpleflatmapper.jdbc.spring.RowMapperImpl;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProfileDAO implements cat.tecnocampus.tinder.application.ProfileDAO {

	private JdbcTemplate jdbcTemplate;

	private final RowMapper<Profile> profileRowMapperLazy = (resultSet, i) -> {
		Profile profile = new Profile();

		profile.setEmail(resultSet.getString("email"));
		profile.setNickname(resultSet.getString("nickname"));
		profile.setGender(Profile.Gender.valueOf(resultSet.getString("gender")));
		profile.setAttraction(Profile.Gender.valueOf(resultSet.getString("attraction")));
		profile.setPassion(Profile.Passion.valueOf(resultSet.getString("passion")));

		return profile;
	};

	ResultSetExtractorImpl<Profile> profilesRowMapper =
			JdbcTemplateMapperFactory
					.newInstance()
					.addKeys("email")
					.newResultSetExtractor(Profile.class);

	RowMapperImpl<Profile> profileRowMapper =
			JdbcTemplateMapperFactory
					.newInstance()
					.addKeys("email")
					.newRowMapper(Profile.class);

	public ProfileDAO(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Profile getProfileLazy(String email) {
		final String queryProfileLazy = "select email, nickname, gender, attraction, passion from tinder_user where email = ?";
		try {
			return jdbcTemplate.queryForObject(queryProfileLazy, new Object[]{email}, profileRowMapperLazy);
		} catch (EmptyResultDataAccessException e) {
			throw new ProfileNotFound(email);
		}
	}

	@Override
	public List<Profile> getProfilesLazy() {
		final String queryProfilesLazy = "select email, nickname, gender, attraction, passion from tinder_user";
		return jdbcTemplate.query(queryProfilesLazy, profileRowMapperLazy);
	}

	@Override
	public Profile getProfile(String email) {
		final String queryProfile = "select u.email as email, u.nickname as nickname, u.gender as gender, u.attraction as attraction, u.passion as passion, " +
				"p.target as likes_target, p.creation_date as likes_creationDate, p.matched as likes_matched, p.match_date as likes_matchDate from tinder_user u left join proposal p on u.email = p.origin where u.email = ?";
		Profile result;
		try {
			result = jdbcTemplate.queryForObject(queryProfile, new Object[]{email}, profileRowMapper);
			cleanEmptyLikes(result);
			return result;
		} catch (EmptyResultDataAccessException e) {
			throw new ProfileNotFound(email);
		}
	}

	@Override
	public List<Profile> getProfiles() {
		final String queryProfiles = "select u.email as email, u.nickname as nickname, u.gender as gender, u.attraction as attraction, u.passion as passion, " +
				"p.target as likes_target, p.creation_date as likes_creationDate, p.matched as likes_matched, p.match_date as likes_matchDate from tinder_user u left join proposal p on u.email = p.origin";
		List<Profile> result;
		result = jdbcTemplate.query(queryProfiles, profilesRowMapper);
		result.stream().forEach(this::cleanEmptyLikes);
		return result;
	}

	//Avoid list of candidates with an invalid like when the profile hasn't any
	private void cleanEmptyLikes(Profile profile) {
		boolean hasNullCandidates = profile.getLikes().stream().anyMatch(c -> c.getTarget() == null);
		if (hasNullCandidates) {
			profile.setLikes(new ArrayList<>());
		}
	}

	@Override
	public String addProfile(Profile profile) {
		final String insertProfile = "INSERT INTO tinder_user (email, nickname, gender, attraction, passion) VALUES (?, ?, ?, ?, ?)";
		jdbcTemplate.update(insertProfile, profile.getEmail(), profile.getNickname(), profile.getGender().toString(),
				profile.getAtraction().toString(), profile.getPassion().toString());

		return profile.getEmail();
	}

	@Override
	public void saveLikes(String origin, List<Like> likes) {
		final String insertProposal = "INSERT INTO proposal (origin, target, matched, creation_date) VALUES (?, ?, ?, ?)";
		jdbcTemplate.batchUpdate(insertProposal, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement preparedStatement, int i) throws SQLException {
				Like like = likes.get(i);
				preparedStatement.setString(1, origin);
				preparedStatement.setString(2, like.getTarget());
				preparedStatement.setBoolean(3, like.isMatched());
				preparedStatement.setDate(4, Date.valueOf(like.getCreationDate()));
			}

			@Override
			public int getBatchSize() {
				return likes.size();
			}
		});
	}

	@Override
	public void updateLikeToMatch(String origin, String target) {
		final String updateProposal = "UPDATE proposal SET matched = true, match_date = ? where origin = ? AND target = ?";
		jdbcTemplate.update(updateProposal, Date.valueOf(LocalDate.now()), origin, target);
	}
}
